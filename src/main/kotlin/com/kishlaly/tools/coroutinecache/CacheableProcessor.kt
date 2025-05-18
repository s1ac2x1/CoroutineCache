package com.kishlaly.tools.coroutinecache

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toTypeName

class CacheableProcessor(
    private val env: SymbolProcessorEnvironment
) : SymbolProcessor {
    private val codeGen = env.codeGenerator
    private val logger = env.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(Cacheable::class.qualifiedName!!)
            .filterIsInstance<KSFunctionDeclaration>()
            .forEach { func -> generateWrapper(func) }
        return emptyList()
    }

    private fun generateWrapper(func: KSFunctionDeclaration) {
        val pkg = func.packageName.asString()
        val original = func.simpleName.asString()
        val wrapper = "${original}Cached"

        // Read annotation args
        val ann = func.annotations.first { it.shortName.asString() == "Cacheable" }
        val ttl = ann.arguments.first { it.name?.asString() == "ttlSeconds" }.value as Long
        val maxSize = ann.arguments.first { it.name?.asString() == "maxSize" }.value as Int
        val useLRU = ann.arguments.first { it.name?.asString() == "useLRU" }.value as Boolean
        val coalesce = ann.arguments.first { it.name?.asString() == "coalesce" }.value as Boolean

        // Build parameters and return type
        val params = func.parameters.map { param ->
            val name = param.name!!.asString()
            val typeName = param.type.resolve().toTypeName()
            ParameterSpec(name, typeName)
        }
        val returnType = func.returnType!!.resolve().toTypeName()

        // Determine key and value TypeNames
        val keyType = params.first().type
        val valueType = returnType

        // Compose key expression
        val keyExpr = if (params.size == 1) params[0].name else "listOf(${params.joinToString { it.name }})"

        // Generate the wrapper function file
        val fileSpec = FileSpec.builder(pkg, wrapper)
            .addFunction(
                FunSpec.builder(wrapper)
                    .addModifiers(KModifier.SUSPEND, KModifier.PUBLIC)
                    .addParameters(params)
                    .returns(returnType)
                    .addStatement(
                        "val cache = %T.getCache<%T, %T>(%S, %T(\n    ttl = java.time.Duration.ofSeconds(%L),\n    maxSize = %L,\n    useLRU = %L,\n    coalesce = %L\n))",
                        CacheManager::class,
                        keyType,
                        valueType,
                        wrapper,
                        CacheConfig::class,
                        ttl,
                        maxSize,
                        useLRU,
                        coalesce
                    )
                    .addStatement("val key = %L", keyExpr)
                    .addStatement(
                        "return cache.getOrPut(key) { %N(${params.joinToString { it.name }}) }",
                        original
                    )
                    .build()
            )
            .build()

        codeGen.createNewFile(
            Dependencies(false, func.containingFile!!),
            pkg,
            wrapper
        ).bufferedWriter(Charsets.UTF_8).use { writer ->
            fileSpec.writeTo(writer)
        }

        logger.info("Generated cache wrapper: $pkg.$wrapper")
    }
}
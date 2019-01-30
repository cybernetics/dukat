package org.jetbrains.dukat.ast

import org.jetbrains.dukat.ast.model.AstNode
import org.jetbrains.dukat.ast.model.declaration.CallSignatureDeclaration
import org.jetbrains.dukat.ast.model.declaration.ClassDeclaration
import org.jetbrains.dukat.ast.model.declaration.ConstructorDeclaration
import org.jetbrains.dukat.ast.model.declaration.Declaration
import org.jetbrains.dukat.ast.model.declaration.DocumentRootDeclaration
import org.jetbrains.dukat.ast.model.declaration.ExpressionDeclaration
import org.jetbrains.dukat.ast.model.declaration.FunctionDeclaration
import org.jetbrains.dukat.ast.model.declaration.HeritageClauseDeclaration
import org.jetbrains.dukat.ast.model.declaration.InterfaceDeclaration
import org.jetbrains.dukat.ast.model.declaration.MethodSignatureDeclaration
import org.jetbrains.dukat.ast.model.declaration.ModifierDeclaration
import org.jetbrains.dukat.ast.model.declaration.ParameterDeclaration
import org.jetbrains.dukat.ast.model.declaration.PropertyDeclaration
import org.jetbrains.dukat.ast.model.declaration.TokenDeclaration
import org.jetbrains.dukat.ast.model.declaration.TypeAliasDeclaration
import org.jetbrains.dukat.ast.model.declaration.TypeParameterDeclaration
import org.jetbrains.dukat.ast.model.declaration.VariableDeclaration
import org.jetbrains.dukat.ast.model.declaration.types.FunctionTypeDeclaration
import org.jetbrains.dukat.ast.model.declaration.types.IndexSignatureDeclaration
import org.jetbrains.dukat.ast.model.declaration.types.ObjectLiteralDeclaration
import org.jetbrains.dukat.ast.model.declaration.types.StringTypeDeclaration
import org.jetbrains.dukat.ast.model.declaration.types.TypeDeclaration
import org.jetbrains.dukat.ast.model.declaration.types.UnionTypeDeclaration

@Suppress("UNCHECKED_CAST")
private fun <T:AstNode> Map<String, Any?>.getEntity(key: String) = (get(key) as Map<String, Any?>?)!!.toAst<T>()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.getEntitiesList(key: String) = get(key) as List<Map<String, Any?>>

private fun <T : Declaration> Map<String, Any?>.getEntities(key: String, mapper: (Map<String, Any?>) -> T = {
    it.toAst()
} ) =
        getEntitiesList(key).map(mapper)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.getInitializerExpression(): ExpressionDeclaration? {
    val initializer = get("initializer") as Map<String, Any?>?
    return initializer?.let {
        val expression = it.toAst<Declaration>()

        if (expression is ExpressionDeclaration) {
            if (expression.kind.value == "@@DEFINED_EXTERNALLY") {
                expression
            } else throw Exception("unkown initializer")
        } else null
    }
}

private fun Map<String, Any?>.parameterDeclarationToAst() =
        ParameterDeclaration(
                get("name") as String,
                (getEntity("type")),
                getInitializerExpression(),
                get("vararg") as Boolean,
                get("optional") as Boolean
        )

@Suppress("UNCHECKED_CAST")
fun <T : AstNode> Map<String, Any?>.toAst(): T {
    val reflectionType = get("reflection") as String
    val res = when (reflectionType) {
        UnionTypeDeclaration::class.simpleName -> UnionTypeDeclaration(
            getEntities("params")
        )
        TokenDeclaration::class.simpleName -> TokenDeclaration(
           get("value") as String
        )
        HeritageClauseDeclaration::class.simpleName -> HeritageClauseDeclaration(
            get(HeritageClauseDeclaration::name.name) as String,
            getEntities(HeritageClauseDeclaration::typeArguments.name),
            get(HeritageClauseDeclaration::extending.name) as Boolean
        )
        TypeAliasDeclaration::class.simpleName -> TypeAliasDeclaration(
            get("aliasName") as String,
            getEntities("typeParameters"),
            getEntity("typeReference")
        )
        StringTypeDeclaration::class.simpleName -> StringTypeDeclaration(
                get(StringTypeDeclaration::tokens.name) as List<String>
        )
        IndexSignatureDeclaration::class.simpleName -> IndexSignatureDeclaration(
                getEntities(IndexSignatureDeclaration::indexTypes.name),
                getEntity(IndexSignatureDeclaration::returnType.name)
        )
        CallSignatureDeclaration::class.simpleName -> CallSignatureDeclaration(
                getEntities("parameters"),
                getEntity("type"),
                getEntities("typeParameters")
        )
        ExpressionDeclaration::class.simpleName -> ExpressionDeclaration(
                (getEntity("kind")),
                get("meta") as String
        )
        ModifierDeclaration::class.simpleName -> ModifierDeclaration(get("token") as String)
        TypeDeclaration::class.simpleName -> TypeDeclaration(if (get("value") is String) {
            get("value") as String
        } else {
            throw Exception("failed to create type declaration from ${this}")
        }, getEntities("params"))
        ConstructorDeclaration::class.simpleName -> ConstructorDeclaration(
                getEntities("parameters"),
                getEntity("type"),
                getEntities("typeParameters"),
                getEntities("modifiers")
        )
        FunctionDeclaration::class.simpleName -> FunctionDeclaration(
                get("name") as String,
                getEntities("parameters"),
                getEntity("type"),
                getEntities("typeParameters"),
                getEntities("modifiers")
        )
        MethodSignatureDeclaration::class.simpleName -> MethodSignatureDeclaration(
                get(MethodSignatureDeclaration::name.name) as String,
                getEntities(MethodSignatureDeclaration::parameters.name),
                getEntity(MethodSignatureDeclaration::type.name),
                getEntities(MethodSignatureDeclaration::typeParameters.name),
                get(MethodSignatureDeclaration::optional.name) as Boolean,
                getEntities(MethodSignatureDeclaration::modifiers.name)
        )
        FunctionTypeDeclaration::class.simpleName -> FunctionTypeDeclaration(
                getEntities("parameters"),
                getEntity("type")
        )
        ParameterDeclaration::class.simpleName -> parameterDeclarationToAst()
        VariableDeclaration::class.simpleName -> VariableDeclaration(get("name") as String, getEntity("type"))
        PropertyDeclaration::class.simpleName -> PropertyDeclaration(
                get("name") as String,
                getEntity("type"),
                getEntities("typeParameters"),
                get("optional") as Boolean,
                getEntities("modifiers")
        )
        DocumentRootDeclaration::class.simpleName -> DocumentRootDeclaration(get("packageName") as String, getEntities("declarations"))
        TypeParameterDeclaration::class.simpleName -> TypeParameterDeclaration(get("name") as String, getEntities("constraints"))
        ClassDeclaration::class.simpleName -> ClassDeclaration(
                get("name") as String,
                getEntities("members"),
                getEntities("typeParameters"),
                getEntities("parentEntities"),
                null,
                getEntities("modifiers")
        )
        InterfaceDeclaration::class.simpleName -> InterfaceDeclaration(
                get("name") as String,
                getEntities("members"),
                getEntities("typeParameters"),
                getEntities("parentEntities")
        )
        ObjectLiteralDeclaration::class.simpleName -> ObjectLiteralDeclaration(getEntities("members"))
        else -> throw Exception("failed to create declaration from mapper: ${this}")
    }

    return res as T
}
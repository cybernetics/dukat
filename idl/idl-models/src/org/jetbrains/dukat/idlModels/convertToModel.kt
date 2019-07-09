package org.jetbrains.dukat.idlModels

import org.jetbrains.dukat.astCommon.IdentifierEntity
import org.jetbrains.dukat.astModel.*
import org.jetbrains.dukat.astModel.CompanionObjectModel
import org.jetbrains.dukat.astModel.ModuleModel
import org.jetbrains.dukat.astModel.SourceFileModel
import org.jetbrains.dukat.astModel.SourceSetModel
import org.jetbrains.dukat.astModel.TopLevelModel
import org.jetbrains.dukat.idlDeclarations.*
import org.jetbrains.dukat.panic.raiseConcern
import org.jetbrains.dukat.translator.ROOT_PACKAGENAME
import java.io.File

fun IDLTypeDeclaration.process(): TypeModel {
    return TypeValueModel(
            value = IdentifierEntity(when (name) {
                "float" -> "Float"
                "double" -> "Double"
                "long" -> "Int"
                "byte" -> "Byte"
                "short" -> "Short"
                "longlong" -> "Long"
                else -> name
            }),
            params = listOf(),
            metaDescription = null
    )
}

fun IDLArgumentDeclaration.process(): ParameterModel {
    return ParameterModel(
            name = name,
            type = type.process(),
            initializer = null,
            vararg = false,
            optional = false
    )
}

fun IDLTopLevelDeclaration.convertToModel(): TopLevelModel? {
    return when (this) {
        is IDLInterfaceDeclaration -> ClassModel(
                name = IdentifierEntity(name),
                members = attributes.mapNotNull { it.process() } +
                        operations.mapNotNull { it.process() },
                companionObject = CompanionObjectModel(
                        name = "",
                        members = listOf(),
                        parentEntities = listOf()
                ),
                typeParameters = listOf(),
                parentEntities = parents.map {
                    HeritageModel(
                            it.process(),
                            listOf(),
                            null
                    )
                },
                primaryConstructor = null,
                annotations = mutableListOf(),
                external = true,
                abstract = true
        )
        else -> raiseConcern("unprocessed top level declaration: ${this}") { null }
    }
}

fun IDLMemberDeclaration.process(): MemberModel? {
    return when (this) {
        is IDLAttributeDeclaration -> PropertyModel(
                name = IdentifierEntity(name),
                type = type.process(),
                typeParameters = listOf(),
                static = false,
                override = false,
                getter = false,
                setter = false,
                open = false
        )
        is IDLOperationDeclaration -> MethodModel(
                name = IdentifierEntity(name),
                parameters = arguments.map { it.process() },
                type = returnType.process(),
                typeParameters = listOf(),
                static = false,
                override = false,
                operator = false,
                annotations = listOf(),
                open = false
        )
        else -> raiseConcern("unprocessed member declaration: ${this}") { null }
    }
}

fun IDLFileDeclaration.process(): SourceSetModel {
    val modelDeclarations = declarations.mapNotNull { it.convertToModel() }

    val module = ModuleModel(
            name = ROOT_PACKAGENAME,
            shortName = ROOT_PACKAGENAME,
            declarations = modelDeclarations,
            annotations = mutableListOf(),
            sumbodules = listOf(),
            imports = mutableListOf()
    )

    val source = SourceFileModel(
            name = null,
            fileName = File(fileName).normalize().absolutePath,
            root = module,
            referencedFiles = listOf()
    )

    return SourceSetModel(listOf(source))
}
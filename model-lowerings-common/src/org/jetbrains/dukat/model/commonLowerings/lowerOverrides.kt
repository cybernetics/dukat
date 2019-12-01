package org.jetbrains.dukat.model.commonLowerings

import org.jetbrains.dukat.astCommon.IdentifierEntity
import org.jetbrains.dukat.astCommon.NameEntity
import org.jetbrains.dukat.astCommon.appendLeft
import org.jetbrains.dukat.astModel.ClassLikeModel
import org.jetbrains.dukat.astModel.ClassModel
import org.jetbrains.dukat.astModel.FunctionTypeModel
import org.jetbrains.dukat.astModel.InterfaceModel
import org.jetbrains.dukat.astModel.MemberModel
import org.jetbrains.dukat.astModel.MethodModel
import org.jetbrains.dukat.astModel.ModuleModel
import org.jetbrains.dukat.astModel.ParameterModel
import org.jetbrains.dukat.astModel.PropertyModel
import org.jetbrains.dukat.astModel.SourceSetModel
import org.jetbrains.dukat.astModel.TopLevelModel
import org.jetbrains.dukat.astModel.TypeModel
import org.jetbrains.dukat.astModel.TypeParameterReferenceModel
import org.jetbrains.dukat.astModel.TypeValueModel
import org.jetbrains.dukat.astModel.transform


private fun TypeModel.isAny(): Boolean {
    return this is TypeValueModel && value == IdentifierEntity("Any")
}

private data class ParentMembers(val fqName: NameEntity, val methods: Map<NameEntity, List<MethodModel>>, val properties: Map<NameEntity, List<PropertyModel>>)

private class OverrideResolver(val context: ModelContext) {

    private fun ClassLikeModel.getKnownParents(): List<ResolvedClassLike<out ClassLikeModel>> {
        return parentEntities.flatMap { heritageModel ->
            val value = context.unalias(heritageModel.value)
            if (value is TypeValueModel) {
                value.resolveClassLike()?.let { resolvedClassLike ->
                    listOf(resolvedClassLike) + resolvedClassLike.classLike.getKnownParents()
                } ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    private fun TypeValueModel.resolveClassLike(): ResolvedClassLike<out ClassLikeModel>? {
        return context.resolve(this.fqName)
    }

    private fun ClassLikeModel.allParentMembers(): List<ParentMembers> {
        return getKnownParents().map { resolvedClassLike ->
            val methods = mutableListOf<MethodModel>()
            val properties = mutableListOf<PropertyModel>()
            resolvedClassLike.classLike.members.forEach {
                when (it) {
                    is MethodModel -> methods.add(it)
                    is PropertyModel -> properties.add(it)
                }
            }

            ParentMembers(
                resolvedClassLike.resolveFqName(),
                methods.groupBy { it.name },
                properties.groupBy { it.name }
            )
        }
    }

    private fun MethodModel.isOverriding(otherMethodModel
                                         : MethodModel): Boolean {
        if (name != otherMethodModel.name) {
            return false
        }

        if (typeParameters.size != otherMethodModel.typeParameters.size) {
            return false
        }

        val parametersAreEquivalent = paramsAreEquivalent(parameters, otherMethodModel.parameters)
        return parametersAreEquivalent && type.isOverriding(otherMethodModel.type)
    }

    private fun PropertyModel.isOverriding(otherPropertyModel: PropertyModel): Boolean {
        return (name == otherPropertyModel.name) && type.isOverriding(otherPropertyModel.type)
    }

    private fun MethodModel.isSpecialCase(): Boolean {

        val returnType = type

        if (name == IdentifierEntity("equals") && parameters.size == 1) {
            val firstParameterType = parameters[0].type
            if (firstParameterType is TypeValueModel && firstParameterType.value == IdentifierEntity("Any") && firstParameterType.nullable) {
                return true
            }
        }

        if (name == IdentifierEntity("hashCode") && parameters.isEmpty() &&
            returnType is TypeValueModel && returnType.value == IdentifierEntity("Number")) {
            return true
        }

        if (name == IdentifierEntity("toString") && parameters.isEmpty()) {
            return true
        }

        return false
    }

    private fun TypeModel.isDynamic(): Boolean {
        return (this is TypeValueModel && value == IdentifierEntity("dynamic"))
    }

    private fun TypeValueModel.isEquivalent(modelB: TypeValueModel): Boolean {
        val a = context.unalias(this)
        val b = context.unalias(modelB)

        return if ((a is TypeValueModel) && (b is TypeValueModel)) {
            a.value == b.value && a.params == b.params && a.nullable == b.nullable
        } else {
            a.isEquivalent(b)
        }
    }

    private fun paramsAreEquivalent(paramsA: List<ParameterModel>, paramsB: List<ParameterModel>): Boolean {
        if (paramsA.size != paramsB.size) {
            return false
        }

        return paramsA
                .zip(paramsB) { a, b ->
                    a.type.isEquivalent(b.type)
                }
                .all { it }
    }

    private fun FunctionTypeModel.isEquivalent(modelB: FunctionTypeModel): Boolean {
        val parametersAreEquivalent = paramsAreEquivalent(parameters, modelB.parameters)
        return parametersAreEquivalent && type.isOverriding(modelB.type)
    }

    private fun TypeModel.isEquivalent(otherParameterType: TypeModel): Boolean {
        if (this == otherParameterType) {
            return true
        }

        if (isDynamic() || otherParameterType.isDynamic()) {
            return true
        }


        if ((this is TypeValueModel) && (otherParameterType is TypeValueModel)) {

            if (isEquivalent(otherParameterType)) {
                return true
            }
        }

        if ((this is FunctionTypeModel) && (otherParameterType is FunctionTypeModel)) {
            if (isEquivalent(otherParameterType)) {
                return true
            }
        }

        if ((this is FunctionTypeModel) && (otherParameterType is TypeValueModel)) {
            if (otherParameterType.value == IdentifierEntity("Function")) {
                return true
            }
        }

        return false
    }


    private fun TypeModel.isOverriding(otherParameterType: TypeModel, box: TypeValueModel? = null): Boolean {
        val inbox = (box == null || box.value == IdentifierEntity("Array"))

        if (isEquivalent(otherParameterType) && inbox) {
            return true
        }

        if (otherParameterType.isAny()) {
            return if (!inbox) {
                this is TypeParameterReferenceModel
            } else {
                true
            }
        }

        if ((this is TypeValueModel) && (otherParameterType is TypeValueModel)) {
            resolveClassLike()?.let { (classLike, _) ->
                otherParameterType.resolveClassLike()?.let { (otherClassLike, _) ->
                    val isSameClass = classLike === otherClassLike
                    val isParentClass = classLike.getKnownParents().map { it.classLike }.contains(otherClassLike)

                    if (params.isEmpty() && otherParameterType.params.isEmpty()) {
                        if (isSameClass || isParentClass) {
                            return true
                        }
                    } else if (params.size == otherParameterType.params.size) {
                        if (isSameClass) {
                            return params.zip(otherParameterType.params).all { (paramA, paramB) ->
                                paramA.type.isOverriding(paramB.type, this)
                            }
                        }
                    }
                }
            }
        }

        return false
    }

    private fun MemberModel.lowerOverrides(
            allSuperDeclarations: List<ParentMembers>
    ): MemberModel {
        return when (this) {
            is MethodModel -> {
                val overriden =
                        allSuperDeclarations.firstOrNull { (_, methods, _) ->
                            methods[name]?.any { method -> isOverriding(method) } == true
                        }?.fqName ?: if (isSpecialCase()) {
                            IdentifierEntity("<SPECIAL-CASE>")
                        } else null

                if (overriden != null) {
                    copy(override = overriden, parameters = parameters.map { param -> param.copy(initializer = null) })
                } else {
                    this
                }
            }
            is PropertyModel -> {
                val overriden = allSuperDeclarations.firstOrNull { (_, _, properties) ->
                    properties[name]?.any { prop -> isOverriding(prop) } == true
                }?.fqName
                copy(override = overriden)
            }
            is ClassModel -> lowerOverrides()
            is InterfaceModel -> lowerOverrides()
            else -> this
        }
    }


    private fun ClassLikeModel.lowerOverrides(): ClassLikeModel {
        val parentMembers = allParentMembers()

        val membersLowered = members.map { member ->
            member.lowerOverrides(parentMembers)
        }

        return when (this) {
            is InterfaceModel -> copy(members = membersLowered)
            is ClassModel -> copy(members = membersLowered)
            else -> this
        }
    }

    fun lowerOverrides(moduleModel: ModuleModel): ModuleModel {
        val loweredDeclarations = moduleModel.declarations.map { declaration ->
            when (declaration) {
                is ClassLikeModel -> declaration.lowerOverrides()
                else -> {
                    declaration
                }
            }
        }
        val loweredSubmodules = moduleModel.submodules.map { lowerOverrides(it) }
        return moduleModel.copy(declarations = loweredDeclarations, submodules = loweredSubmodules)
    }
}

private fun TopLevelModel.updateContext(context: ModelContext, ownerName: NameEntity) {
    context.register(this, ownerName)
    if (this is ClassLikeModel) {
        members.filterIsInstance(ClassLikeModel::class.java).forEach() {
            context.register(it, ownerName.appendLeft(name))
        }
    }
}

private fun ModuleModel.updateContext(context: ModelContext) {
    for (declaration in declarations) {
        declaration.updateContext(context, name)
    }

    submodules.forEach { declaration -> declaration.updateContext(context) }
}

private fun SourceSetModel.updateContext(astContext: ModelContext) {
    sources.map { source -> source.root.updateContext(astContext) }
}

fun SourceSetModel.lowerOverrides(stdlib: SourceSetModel?): SourceSetModel {
    val astContext = ModelContext()

    stdlib?.updateContext(astContext)
    updateContext(astContext)

    val overrideResolver = OverrideResolver(astContext)
    return transform {
        overrideResolver.lowerOverrides(it)
    }
}
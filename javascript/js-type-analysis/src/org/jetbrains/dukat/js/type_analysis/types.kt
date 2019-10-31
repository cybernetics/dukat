package org.jetbrains.dukat.js.type_analysis

import org.jetbrains.dukat.js.interpretation.Scope
import org.jetbrains.dukat.js.type_analysis.constraint.container.ConstraintContainer
import org.jetbrains.dukat.js.type_analysis.constraint.container.ReturnConstraintContainer
import org.jetbrains.dukat.panic.raiseConcern
import org.jetbrains.dukat.tsmodel.BlockDeclaration
import org.jetbrains.dukat.tsmodel.CallSignatureDeclaration
import org.jetbrains.dukat.tsmodel.ClassDeclaration
import org.jetbrains.dukat.tsmodel.ConstructorDeclaration
import org.jetbrains.dukat.tsmodel.EnumDeclaration
import org.jetbrains.dukat.tsmodel.ExportAssignmentDeclaration
import org.jetbrains.dukat.tsmodel.ModuleDeclaration
import org.jetbrains.dukat.tsmodel.SourceFileDeclaration
import org.jetbrains.dukat.tsmodel.SourceSetDeclaration
import org.jetbrains.dukat.tsmodel.ExpressionStatementDeclaration
import org.jetbrains.dukat.tsmodel.FunctionDeclaration
import org.jetbrains.dukat.tsmodel.ImportEqualsDeclaration
import org.jetbrains.dukat.tsmodel.InterfaceDeclaration
import org.jetbrains.dukat.tsmodel.MemberDeclaration
import org.jetbrains.dukat.tsmodel.MethodSignatureDeclaration
import org.jetbrains.dukat.tsmodel.PropertyDeclaration
import org.jetbrains.dukat.tsmodel.ReturnStatementDeclaration
import org.jetbrains.dukat.tsmodel.TopLevelDeclaration
import org.jetbrains.dukat.tsmodel.TypeAliasDeclaration
import org.jetbrains.dukat.tsmodel.VariableDeclaration
import org.jetbrains.dukat.tsmodel.types.IndexSignatureDeclaration

fun FunctionDeclaration.introduceTypes() : FunctionDeclaration {
    if (this.body != null) {
        val functionScope = Scope<ConstraintContainer>()

        var returnTypeConstraints = ReturnConstraintContainer()
        val parameterConstraintContainers = MutableList(parameters.size) { i ->
            // Store constraints of parameters in scope,
            // and in parameter list (in case the variable is replaced)
            val parameterConstraintContainer = ConstraintContainer()
            functionScope[parameters[i].name] = parameterConstraintContainer
            parameterConstraintContainer
        }

        for(statement in this.body!!.statements) {
            when(statement) {
                is VariableDeclaration -> functionScope[statement.name] = statement.initializer.calculateConstraints(functionScope)
                is ExpressionStatementDeclaration -> statement.expression.calculateConstraints(functionScope)
                is ReturnStatementDeclaration -> returnTypeConstraints = ReturnConstraintContainer(statement.expression.calculateConstraints(functionScope))
                else -> raiseConcern("Cannot derive types in function with statement of type <${statement::class}>") {  }
            }
        }

        return copy(
                type = returnTypeConstraints.resolveToType(),
                parameters = parameterConstraintContainers.mapIndexed { i, parameterConstraints ->
                    parameters[i].copy(
                            type = parameterConstraints.resolveToType()
                    )
                }
        )
    } else {
        return this;
    }
}

fun ConstructorDeclaration.introduceTypes() : ConstructorDeclaration {
    // TODO add body to constructor in AST and process it like a function
    return this
}

fun MemberDeclaration.introduceTypes(): MemberDeclaration {
    return when (this) {
        is FunctionDeclaration -> this.introduceTypes()
        is ConstructorDeclaration -> this.introduceTypes()
        is PropertyDeclaration -> this
        is IndexSignatureDeclaration -> this
        is MethodSignatureDeclaration -> this
        is CallSignatureDeclaration -> this
        else -> raiseConcern("Unexpected member entity type <${this::class}>") { this }
    }
}

fun ClassDeclaration.introduceTypes() = copy(members = members.map { it.introduceTypes() })

fun InterfaceDeclaration.introduceTypes() = copy(members = members.map { it.introduceTypes() })

fun BlockDeclaration.introduceTypes() = copy(statements = statements.map { it.introduceTypes() })

fun TopLevelDeclaration.introduceTypes(): TopLevelDeclaration {
    return when (this) {
        is FunctionDeclaration -> this.introduceTypes()
        is BlockDeclaration -> this.introduceTypes()
        is ClassDeclaration -> this.introduceTypes()
        is InterfaceDeclaration -> this.introduceTypes()
        is ModuleDeclaration -> this.introduceTypes()
        is VariableDeclaration,
        is EnumDeclaration,
        is ExportAssignmentDeclaration,
        is ImportEqualsDeclaration,
        is TypeAliasDeclaration,
        is ReturnStatementDeclaration,
        is ExpressionStatementDeclaration -> this
        else -> raiseConcern("Unexpected top level entity type <${this::class}>") { this }
    }
}

fun ModuleDeclaration.introduceTypes(): ModuleDeclaration = copy(declarations = declarations.map { it.introduceTypes() })

fun SourceFileDeclaration.introduceTypes(): SourceFileDeclaration {
    return if (fileName.endsWith(".d.ts")) { //TODO replace with ".js"
        copy(root = root.introduceTypes())
    } else this
}

fun SourceSetDeclaration.introduceTypes() = copy(sources = sources.map{ it.introduceTypes() })
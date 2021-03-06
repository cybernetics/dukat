package org.jetbrains.dukat.astModel.statements

import org.jetbrains.dukat.astCommon.Entity
import org.jetbrains.dukat.astModel.expressions.ExpressionModel

data class CaseModel(
    val condition: List<ExpressionModel>?,
    val body: BlockStatementModel
) : Entity
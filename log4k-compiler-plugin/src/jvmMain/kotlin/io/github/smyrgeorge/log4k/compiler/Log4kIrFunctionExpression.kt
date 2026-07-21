package io.github.smyrgeorge.log4k.compiler

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrType

class Log4kIrFunctionExpression(
    override var startOffset: Int,
    override var endOffset: Int,
    override var type: IrType,
    override var origin: IrStatementOrigin,
    override var function: IrSimpleFunction,
) : IrFunctionExpression() {
    override var attributeOwnerId: IrElement = this
}

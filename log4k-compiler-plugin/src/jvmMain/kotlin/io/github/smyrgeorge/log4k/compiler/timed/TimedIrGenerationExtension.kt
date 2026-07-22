package io.github.smyrgeorge.log4k.compiler.timed

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class TimedIrGenerationExtension(
    private val configuration: CompilerConfiguration,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val messageCollector = configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE]
        val sourceFile = moduleFragment.files.firstOrNull() ?: return
        val finder = pluginContext.finderForSource(sourceFile)
        val transformer = TimedIrTransformer(pluginContext, finder, messageCollector)
        if (!transformer.isReady) return
        moduleFragment.transform(transformer, null)
        // Attach any synthesized `_meter_` fields now that the module traversal is complete.
        transformer.commit()
    }
}

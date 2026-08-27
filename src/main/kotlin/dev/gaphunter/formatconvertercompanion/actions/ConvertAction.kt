package dev.gaphunter.formatconvertercompanion.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gaphunter.formatconvertercompanion.ConversionService
import dev.gaphunter.formatconvertercompanion.Format
import dev.gaphunter.formatconvertercompanion.model.FormatConversionException
import dev.gaphunter.formatconvertercompanion.review.ReviewPrompt

/**
 * Converts the current selection (or, with no selection, the whole
 * document) to [target] in place -- directly answers the cited competitor
 * complaint about being forced through a file-path dialog instead of
 * converting editor content directly. Parsing/serialization run off the EDT
 * (ApplicationManager.executeOnPooledThread) so a large paste never freezes
 * the UI -- the other cited complaint ("makes the IDE unresponsive").
 */
abstract class ConvertAction(private val target: Format) : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val document = editor.document
        val selectionModel = editor.selectionModel

        val rangeStart: Int
        val rangeEnd: Int
        val sourceText: String
        if (selectionModel.hasSelection()) {
            rangeStart = selectionModel.selectionStart
            rangeEnd = selectionModel.selectionEnd
            sourceText = selectionModel.selectedText.orEmpty()
        } else {
            rangeStart = 0
            rangeEnd = document.textLength
            sourceText = document.text
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val convertedText = try {
                ConversionService.convert(sourceText, target)
            } catch (ex: FormatConversionException) {
                showError(project, ex.message ?: "Conversion failed")
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project, "Convert to ${target.label}", null, {
                    document.replaceString(rangeStart, rangeEnd, convertedText)
                })
                // Real successful conversion only -- never the error branch above.
                ReviewPrompt.recordHit(project)
            }
        }
    }

    private fun showError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Format Conversion Failed")
        }
    }
}

class ConvertToJsonAction : ConvertAction(Format.JSON)
class ConvertToYamlAction : ConvertAction(Format.YAML)
class ConvertToXmlAction : ConvertAction(Format.XML)

package io.github.litscoped

import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.javascript.JSLanguageDialect
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil

/** Adds ES module imports for element classes. */
object Imports {
    private val RELATIVE_PATH = Regex("""from\s+['"](\.{1,2}/[^'"]+)['"]""")
    private val EXTENSION = Regex("""\.(m?[jt]sx?)$""")

    /**
     * Imports [cls] into [file] unless it is declared or imported there already. The path is relative and
     * follows the file's style: with an extension when its relative imports have one (`.ts` becomes `.js`,
     * the ES module convention), without otherwise.
     */
    fun ensureImported(file: PsiFile, cls: JSClass) {
        val name = cls.name ?: return
        val target = cls.containingFile?.virtualFile ?: return
        val origin = file.originalFile.virtualFile ?: return
        if (origin == target) return
        if (PsiTreeUtil.findChildrenOfType(file, JSClass::class.java).any { it.name == name }) return

        val imports = PsiTreeUtil.getChildrenOfTypeAsList(file, ES6ImportDeclaration::class.java)
        val word = Regex("""\b${Regex.escape(name)}\b""")
        if (imports.any { word.containsMatchIn(it.text.substringBefore(" from ")) }) return

        var path = FileUtil.getRelativePath(origin.parent.path, target.path, '/') ?: return
        if (!path.startsWith(".")) path = "./$path"
        val relativeImports = imports.mapNotNull { RELATIVE_PATH.find(it.text)?.groupValues?.get(1) }
        val withExtension = relativeImports.isEmpty() || relativeImports.any { EXTENSION.containsMatchIn(it) }
        path = if (withExtension) path.replace(Regex("""\.tsx?$"""), ".js") else path.replace(EXTENSION, "")

        val isDefault = cls.text.startsWith("export default") || cls.parent?.text?.startsWith("export default") == true
        val text = if (isDefault) "import $name from '$path';" else "import { $name } from '$path';"
        if (file.language !is JSLanguageDialect) return
        val statement = JSPsiElementFactory.createJSSourceElement(text, file, ES6ImportDeclaration::class.java)
        val newline = PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n")
        val last = imports.lastOrNull()
        if (last != null) {
            val added = file.addAfter(statement, last)
            file.addBefore(newline, added)
        } else {
            val added = file.addBefore(statement, file.firstChild)
            file.addAfter(newline, added)
        }
    }
}

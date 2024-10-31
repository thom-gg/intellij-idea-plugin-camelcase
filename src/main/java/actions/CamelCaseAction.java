package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringFactory;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.command.WriteCommandAction;



public class CamelCaseAction extends AnAction{

    private String convertToCamelCase(String input) {
        // We count upper case letters, so if the name is fully uppercase, we don't rename it (some constants needs to be fully uppercase)
        int upperCaseCounter = 0;
        StringBuilder builder = new StringBuilder();

        boolean addUpperCase = false;
        for (int i = 0; i<input.length(); i++) {
            char c = input.charAt(i);
            if (Character.toUpperCase(c) == c) {upperCaseCounter += 1;}
            if (c == '_') {
                addUpperCase = true; // next word should start with an upper case letter
                continue;
            }
            if (i == 0) {
                builder.append(Character.toLowerCase(c));
            }
            else {
                if (addUpperCase) {
                    builder.append(Character.toUpperCase(c));
                    addUpperCase = false;
                }
                else {
                    builder.append(c);
                }
            }
        }
        // As said above, we don't want to rename fully uppercase constants
        if (upperCaseCounter == input.length()) {
            return input;
        }
        return builder.toString();
    }

    // Rename all variables in a file
    private void renameOnFile(PsiFile psiFile, Project project) {
        psiFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                RefactoringFactory refactoringFactory = RefactoringFactory.getInstance(project);
                super.visitElement(element);
                // For references like this.variable or super.variable
                if (element instanceof PsiReferenceExpression) {
                    PsiReferenceExpression ref = (PsiReferenceExpression) element;
                    PsiElement qualifier = ref.getQualifier();
                    // We don't rename super references (super.variable ...) as it might break things if it's called only on one file
                    // So we rename only this (this.variable) references
                    if (qualifier instanceof PsiThisExpression) {
                        String currentName = element.getText().split("\\.")[1];
                        String camelCase = convertToCamelCase(currentName);
                        // We check if it's not already in camel case, to avoid performing useless operations
                        if (!camelCase.equals(currentName)) {
                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                try {
                                    ref.handleElementRename(camelCase);

                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            });
                        }
                    }
                }
                else if (element instanceof PsiVariable) {
                    PsiVariable variable = (PsiVariable) element;
                    String currentName = variable.getName();
                    String camelCase = convertToCamelCase(currentName);
                    // Here again we check if it's not already in camel case
                    if (!camelCase.equals(currentName)) {
                        refactoringFactory.createRename(variable, camelCase).run();
                    }
                }
            }
        });
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        VirtualFile folder = e.getDataContext().getData(CommonDataKeys.VIRTUAL_FILE);
        Project project = e.getData(CommonDataKeys.PROJECT);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        // If the action has been called on a repository, we're going to act on all files inside
        if (psiFile == null && folder!=null && folder.isDirectory()) {
            VirtualFile[] files = folder.getChildren();

            if (files.length==0) {
                Messages.showInfoMessage("There is no files in this directory", "Renaming Variables to Camel Case");
                return;
            }
            // Confirmation dialog box
            String[] options = {"Rename", "Cancel"};
            int result = Messages.showDialog("Do you want to rename all variables (except uppercase constants) to camelCase, in " + files.length + " files of this folder ("+folder.getName() +") ?",
                    "Renaming Variables to Camel Case", options, 0, null);

            if (result == 0) {
                for (VirtualFile file : files) {
                    psiFile = PsiManager.getInstance(project).findFile(file);
                    if (psiFile != null) {
                        renameOnFile(psiFile, project);
                    }
                }
                Messages.showInfoMessage("Successfully renamed variables in " + files.length + " to camel case", "Success");
            }
        }
        // If it was called on a single file, act on this file
        else if (psiFile != null) {
            // Confirmation dialog box
            String[] options = {"Rename", "Cancel"};
            int result = Messages.showDialog("Do you want to rename all variables (except uppercase constants) to camelCase, in " + psiFile.getName() + " ?",
                    "Renaming Variables to Camel Case", options, 0, null);
            if (result == 0) {
                renameOnFile(psiFile, project);
                Messages.showInfoMessage("Successfully renamed variables in " + psiFile.getName() + " to camel case", "Success");

            }

        }

    }
}

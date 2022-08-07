package rprop.tools;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.utils.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Optional;

public final class JavaObfuscator {
    public static void main(final String[] args) throws FileNotFoundException {
        final CompilationUnit unit = StaticJavaParser.parse(new File("src\\main\\java\\rprop\\tools\\JavaObfuscator.java"));
        processCompilationUnit(unit);
        processCompilationUnit(unit);
        System.out.println(processCompilationUnit(unit));
    }

    public static CompilationUnit processCompilationUnit(final CompilationUnit unit) {
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(classOrInterface -> {
            if (classOrInterface.isInterface()) return;

            final Dispatch staticDispatch = new Dispatch(classOrInterface, true);
            final Dispatch instanceDispatch = new Dispatch(classOrInterface, false);
            classOrInterface.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
                final Optional<Node> parentNode = methodDeclaration.getParentNode();
                if (parentNode.isEmpty() || parentNode.get() != classOrInterface) return;

                final Optional<BlockStmt> blockStmtOpt = methodDeclaration.getBody();
                if (blockStmtOpt.isEmpty()) return;

                final Dispatch dispatch = methodDeclaration.isStatic() ? staticDispatch : instanceDispatch;

                final NodeList<Parameter> methodParameters = methodDeclaration.getParameters();
                final NodeList<Expression> methodArguments = new NodeList<>();
                methodArguments.add(new IntegerLiteralExpr(String.valueOf(++dispatch.dispatchIndex)));
                final NodeList<Statement> switchEntryBlockStatements = new NodeList<>();
                for (int index = 0; index < methodParameters.size(); ++index) {
                    final Parameter parameter = methodParameters.get(index);
                    final String parameterName = "dispatch" + dispatch.dispatchVersion + "_arg" + index;
                    if (index >= dispatch.dispatchParameters.size()) {
                        dispatch.dispatchParameters.add(Helper.createFinalParameter(parameter.getType(), parameterName));
                    } else {
                        final Parameter dispatchParameter = dispatch.dispatchParameters.get(index);
                        if (!dispatchParameter.getType().equals(parameter.getType())) {
                            dispatch.dispatchParameters.set(index, Helper.createFinalParameter(Dispatch.sObjectType, parameterName));
                        }
                    }
                    methodArguments.add(parameter.getNameAsExpression());
                    switchEntryBlockStatements.add(new ExpressionStmt(new VariableDeclarationExpr(new VariableDeclarator(
                            parameter.getType(), parameter.getName(), new CastExpr(parameter.getType(), new NameExpr(parameterName))))));
                }
                final BlockStmt blockStmt = blockStmtOpt.get();
                switchEntryBlockStatements.addAll(blockStmt.getStatements());
                if (methodDeclaration.getType().isVoidType()) {
                    switchEntryBlockStatements.forEach(Helper::fixVoidStatement);
                    switchEntryBlockStatements.add(new BreakStmt());
                }
                final NodeList<Statement> switchEntryStatements = NodeList.nodeList(new BlockStmt(switchEntryBlockStatements));
                final SwitchEntry switchEntry = new SwitchEntry(NodeList.nodeList(methodArguments.get(0)),
                        SwitchEntry.Type.STATEMENT_GROUP, switchEntryStatements);
                dispatch.dispatchSwitchEntries.add(switchEntry);

                final MethodCallExpr callDispatchExpr = new MethodCallExpr(null, dispatch.dispatchMethod.getName(), methodArguments);
                final NodeList<Statement> noTryStatements = NodeList.nodeList(methodDeclaration.getType().isVoidType() ?
                        new ExpressionStmt(callDispatchExpr) :
                        new ReturnStmt(new CastExpr(methodDeclaration.getType(), callDispatchExpr)));
                final NodeList<Statement> tryStatements = Helper.buildTryStmt(dispatch, methodDeclaration, noTryStatements);
                blockStmt.setStatements(noTryStatements);
                dispatch.dispatchMethodArgumentsList.add(methodArguments);
                if (tryStatements != null) {
                    dispatch.dispatchMethodTryList.add(new Pair<>(methodDeclaration, tryStatements));
                }
            });

            staticDispatch.commit(classOrInterface);
            instanceDispatch.commit(classOrInterface);
        });
        return unit;
    }

    public static final class Dispatch {
        public static final NullLiteralExpr sNullLiteralExpr = new NullLiteralExpr();
        @SuppressWarnings("deprecation")
        public static final ClassOrInterfaceType sObjectType = new ClassOrInterfaceType(Object.class.getName());
        @SuppressWarnings("deprecation")
        public static final ClassOrInterfaceType sThrowableType = new ClassOrInterfaceType(Throwable.class.getName());
        @SuppressWarnings("deprecation")
        public static final ClassOrInterfaceType sExceptionType = new ClassOrInterfaceType(Exception.class.getName());
        public static final NodeList<Modifier> sPrivateStaticModifierList = Modifier.createModifierList(Modifier.Keyword.PRIVATE,
                Modifier.Keyword.STATIC);
        public static final NodeList<Modifier> sPrivateModifierList = Modifier.createModifierList(Modifier.Keyword.PRIVATE);

        public final MethodDeclaration dispatchMethod;
        public final NodeList<Parameter> dispatchParameters = new NodeList<>();
        public final Parameter dispatchIndexParameter;
        public final NodeList<SwitchEntry> dispatchSwitchEntries = new NodeList<>();

        public final LinkedList<NodeList<Expression>> dispatchMethodArgumentsList = new LinkedList<>();

        public final LinkedList<Pair<MethodDeclaration, NodeList<Statement>>> dispatchMethodTryList = new LinkedList<>();

        public int dispatchIndex = -1, dispatchVersion = 0;

        public Dispatch(final ClassOrInterfaceDeclaration classDeclaration, final boolean isStatic) {
            final String name = (isStatic ? "doCommandStatic" : "doCommandInstance") + classDeclaration.getNameAsString();
            String finalName = name;
            while (!classDeclaration.getMethodsByName(finalName).isEmpty()) {
                finalName = name + (++dispatchVersion);
            }
            this.dispatchMethod = new MethodDeclaration(isStatic ? sPrivateStaticModifierList : sPrivateModifierList,
                    sObjectType, finalName);
            this.dispatchIndexParameter = Helper.createFinalParameter(PrimitiveType.intType(),
                    finalName + "Index");
        }

        public void commit(final ClassOrInterfaceDeclaration classDeclaration) {
            if (dispatchIndex >= 0) {
                final BlockStmt dispatchBody = dispatchMethod.createBody();
                dispatchBody.addStatement(new SwitchStmt(dispatchIndexParameter.getNameAsExpression(), dispatchSwitchEntries));
                dispatchBody.addStatement(new ReturnStmt(Dispatch.sNullLiteralExpr));
                dispatchParameters.addFirst(dispatchIndexParameter);
                dispatchMethod.setParameters(dispatchParameters);
                // fix arguments
                for (final NodeList<Expression> methodArguments : dispatchMethodArgumentsList) {
                    while (methodArguments.size() < dispatchParameters.size()) {
                        final Type extraType = dispatchParameters.get(methodArguments.size()).getType();
                        if (extraType.isPrimitiveType()) {
                            switch (extraType.asPrimitiveType().getType()) {
                                case BOOLEAN:
                                    methodArguments.add(new BooleanLiteralExpr(false));
                                    break;
                                case CHAR:
                                case BYTE:
                                case SHORT:
                                case INT:
                                case LONG:
                                case FLOAT:
                                case DOUBLE:
                                default:
                                    methodArguments.add(new IntegerLiteralExpr("0"));
                                    break;
                            }
                        } else {
                            methodArguments.add(Dispatch.sNullLiteralExpr);
                        }
                    }
                }
                // fix exceptions
                final NodeList<ReferenceType> dispatchMethodThrownExceptions = dispatchMethod.getThrownExceptions();
                boolean hasException = false;
                for (final ReferenceType referenceType : dispatchMethodThrownExceptions) {
                    final String referenceTypeName = referenceType.asClassOrInterfaceType().asString();
                    if (Helper.isThrowable(referenceTypeName)) {
                        dispatchMethodThrownExceptions.clear();
                        dispatchMethodThrownExceptions.add(sThrowableType);
                        hasException = false;
                        break;
                    }
                    if (Helper.isException(referenceTypeName)) {
                        hasException = true;
                    }
                }
                if (hasException) {
                    dispatchMethodThrownExceptions.clear();
                    dispatchMethodThrownExceptions.add(sExceptionType);
                }
                if (dispatchMethodThrownExceptions.isNonEmpty()) {
                    for (final Pair<MethodDeclaration, NodeList<Statement>> pair : dispatchMethodTryList) {
                        boolean allMeet = true;
                        final NodeList<ReferenceType> thrownExceptions = pair.a.getThrownExceptions();
                        for (final ReferenceType referenceType : dispatchMethodThrownExceptions) {
                            if (!thrownExceptions.contains(referenceType)) {
                                allMeet = false;
                                break;
                            }
                        }
                        if (!allMeet) {
                            // noinspection OptionalGetWithoutIsPresent
                            pair.a.getBody().get().setStatements(pair.b);
                        }
                    }
                }
                classDeclaration.addMember(dispatchMethod);
            }
        }
    }

    public static final class Helper {
        public static final NodeList<Modifier> sFinalModifierList = Modifier.createModifierList(Modifier.Keyword.FINAL);

        public static final Statement sThrowRuntimeExceptionStatement = StaticJavaParser.parseStatement(
                "throw tr instanceof java.lang.RuntimeException ? (java.lang.RuntimeException)tr : new java.lang.RuntimeException(tr);");

        public static Parameter createFinalParameter(final Type type, final String name) {
            return new Parameter(sFinalModifierList, type, new SimpleName(name));
        }

        public static void fixVoidStatement(final Statement statement) {
            if (statement.isReturnStmt()) {
                statement.asReturnStmt().setExpression(Dispatch.sNullLiteralExpr);
                return;
            }
            if (statement.isIfStmt()) {
                final IfStmt ifStmt = statement.asIfStmt();
                fixVoidStatement(ifStmt.getThenStmt());
                ifStmt.getElseStmt().ifPresent(Helper::fixVoidStatement);
                return;
            }
            if (statement.isDoStmt()) {
                fixVoidStatement(statement.asDoStmt().getBody());
                return;
            }
            if (statement.isWhileStmt()) {
                fixVoidStatement(statement.asWhileStmt().getBody());
                return;
            }
            if (statement.isTryStmt()) {
                final TryStmt tryStmt = statement.asTryStmt();
                fixVoidStatement(tryStmt.getTryBlock());
                tryStmt.getCatchClauses().forEach(catchClause -> fixVoidStatement(catchClause.getBody()));
                // tryStmt.getFinallyBlock().ifPresent(Helper::fixVoidStatement);
                return;
            }
            if (statement.isBlockStmt()) {
                statement.asBlockStmt().getStatements().forEach(Helper::fixVoidStatement);
            }
        }

        public static boolean isThrowable(final String referenceTypeName) {
            return referenceTypeName.equals(Throwable.class.getName()) || referenceTypeName.equals("Throwable");
        }

        public static boolean isException(final String referenceTypeName) {
            return referenceTypeName.equals(Exception.class.getName()) || referenceTypeName.equals("Exception");
        }

        public static NodeList<Statement> buildTryStmt(final Dispatch dispatch, final MethodDeclaration methodDeclaration,
                                                       final NodeList<Statement> noTryStatements) {
            StringBuilder catchClauseTypes = null;
            final NodeList<ReferenceType> thrownExceptions = methodDeclaration.getThrownExceptions();
            if (thrownExceptions != null && !thrownExceptions.isEmpty()) {
                final NodeList<ReferenceType> dispatchMethodThrownExceptions = dispatch.dispatchMethod.getThrownExceptions();
                for (final ReferenceType referenceType : thrownExceptions) {
                    final ClassOrInterfaceType classOrInterfaceType = referenceType.asClassOrInterfaceType();
                    final String referenceTypeName = classOrInterfaceType.asString();
                    if (isThrowable(referenceTypeName)) {
                        thrownExceptions.clear();
                        thrownExceptions.add(Dispatch.sThrowableType);
                        dispatchMethodThrownExceptions.clear();
                        dispatchMethodThrownExceptions.add(Dispatch.sThrowableType);
                        return null;
                    }
                    if (isException(referenceTypeName)) {
                        classOrInterfaceType.setName(Dispatch.sExceptionType.getName());
                    }
                    if (!dispatchMethodThrownExceptions.contains(classOrInterfaceType)) {
                        dispatchMethodThrownExceptions.add(classOrInterfaceType);
                    }
                    if (catchClauseTypes != null) {
                        catchClauseTypes.append(" | ").append(referenceTypeName);
                    } else {
                        catchClauseTypes = new StringBuilder(referenceTypeName);
                    }
                }
            }

            final BlockStmt lastCatchBlock = new BlockStmt();
            lastCatchBlock.addStatement(sThrowRuntimeExceptionStatement);
            final CatchClause lastCatchClause = new CatchClause(new Parameter(Helper.sFinalModifierList,
                    Dispatch.sThrowableType, new SimpleName("tr")), lastCatchBlock);

            final BlockStmt tryBlock = new BlockStmt(noTryStatements);
            final NodeList<CatchClause> catchClauses = new NodeList<>(lastCatchClause);
            if (catchClauseTypes != null) {
                final SimpleName e = new SimpleName("e");
                // noinspection deprecation
                catchClauses.addFirst(new CatchClause(new Parameter(Helper.sFinalModifierList,
                        new ClassOrInterfaceType(catchClauseTypes.toString()), e),
                        new BlockStmt(NodeList.nodeList(new ThrowStmt(new NameExpr(e))))));
            }
            final TryStmt tryStmt = new TryStmt(tryBlock, catchClauses, null);
            return NodeList.nodeList(tryStmt);
        }
    }
}

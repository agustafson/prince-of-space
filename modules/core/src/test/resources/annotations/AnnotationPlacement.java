package fixtures;

import java.lang.annotation.*;
import java.util.*;
import java.util.function.*;
import java.io.*;

// abstract-interface-return-next-line
interface AbstractInterfaceReturnNextLine { public
    @U("return") String m(); }

// annotation-before-method-type-parameters
class AnnotationBeforeMethodTypeParameters {
    public @U("return") <T> T m(T value) { return value; }
}


// annotation-between-modifier-lines
class AnnotationBetweenModifierLines {
    public
    @U("return")
    static String m() { return null; }
}


// annotation-member
@interface AnnotationMember { @U("return") String value(); }

// annotation-member-after-public
@interface AnnotationMemberAfterPublic { public @U("return") String value(); }

// array-return
class ArrayReturn {
    public @U("element") String @U("array") [] m() { return null; }
}


// array-return-after-name
class ArrayReturnAfterName {
    public @U("element") String m() @U("array") [] { return null; }
}


// array-return-mixed-dimensions
class ArrayReturnMixedDimensions {
    public @U("element") String @U("left") [] m() @U("right") [] { return null; }
}


// cast
class Cast {
    String m(Object value) { return (@U("cast") String) value; }
}


// class-declaration-annotation
@D("class") class ClassDeclarationAnnotation {}

// class-repeated-type-parameter-annotations
class ClassRepeatedTypeParameterAnnotations<@R("a") @R("b") T> {}

// class-supertypes
class ClassSupertypes extends @U("superclass") ArrayList<@U("argument") String> implements @U("interface") Cloneable {}

// class-type-parameter-and-bounds
class ClassTypeParameterAndBounds<@P("T") T extends @U("class-bound") Number & @U("interface-bound") Comparable<@U("argument") T>> {}

// class-type-use-on-type-parameter
class ClassTypeUseOnTypeParameter<@U("T") T> {}

// constructor-annotation-after-public
class ConstructorAnnotationAfterPublic {
    public @U("constructed") ConstructorAnnotationAfterPublic() {}
}


// constructor-annotations
class ConstructorAnnotations {
    @D("constructor") @U("constructed")
    public ConstructorAnnotations(@U("parameter") String value) {}
}


// constructor-receiver
class ConstructorReceiver {
    class Inner { Inner(@U("outer") ConstructorReceiver ConstructorReceiver.this) {} }
}


// constructor-type-argument
class ConstructorTypeArgument {
    <T> ConstructorTypeArgument(T value) {} ConstructorTypeArgument m() { return new <@U("argument") String> ConstructorTypeArgument("x"); }
}


// declaration-after-modifier-next-line
class DeclarationAfterModifierNextLine {
    private
    @D("method") String m() { return null; }
}


// declaration-after-public
class DeclarationAfterPublic {
    public @D("method") String m() { return null; }
}


// declaration-between-modifiers
class DeclarationBetweenModifiers {
    public @D("method") static String m() { return null; }
}


// declaration-indented
class DeclarationIndented {
                @D("method")
    public String m() { return null; }
}


// declarations-same-line-as-public
class DeclarationsSameLineAsPublic {
    @Deprecated @D("method") public String m() { return null; }
}


// declarations-separate-lines
class DeclarationsSeparateLines {
    @Deprecated
    @D("method")
    public @U("return") String m() { return null; }
}


// default-interface-between-modifiers
interface DefaultInterfaceBetweenModifiers { public @U("return") default String m() { return null; } }

// dual-target-next-line
class DualTargetNextLine {
    public
    @B("both") String m() { return null; }
}


// enum-constant
enum EnumConstant { @D("constant") VALUE; }

// field-array-different-annotations
class FieldArrayDifferentAnnotations {
    String a @U("a") [], b @U("b") [];
}


// field-array-extra-dimension
class FieldArrayExtraDimension {
    String @U("common") [] a, b @U("extra") [];
}


// field-array-extra-dimensions
class FieldArrayExtraDimensions {
    String @U("common") [] a, b @U("b1") [] @U("b2") [];
}


// field-array-shared-dimensions
class FieldArraySharedDimensions {
    String @U("outer") [] @U("inner") [] a, b;
}


// field-declaration-and-type
class FieldDeclarationAndType {
    @D("declaration")
    public @U("type") String value;
}


// field-dual-target
class FieldDualTarget {
    public @B("both") String value;
}


// field-generic-arguments
class FieldGenericArguments {
    Map<@U("key") String, List<@U("wildcard") ? extends @U("bound") Number>> value;
}


// field-type-between-modifiers
class FieldTypeBetweenModifiers {
    public @U("type") static String value;
}


// field-type-next-line
class FieldTypeNextLine {
    public
    @U("type") String value;
}


// field-wildcard-super
class FieldWildcardSuper {
    List<@U("wildcard") ? super @U("bound") String> value;
}


// generic-constructor
class GenericConstructor {
    @D("constructor") public <@P("T") T extends @U("bound") Number> GenericConstructor(@U("parameter") T value) {}
}


// generic-return-and-type-parameter
class GenericReturnAndTypeParameter {
    public <@P("T") T extends @U("bound") Number> @U("return") T m(@U("parameter") T value) { return value; }
}


// generic-return-next-line
class GenericReturnNextLine {
    public <T>
    @U("return") T m(T value) { return value; }
}


// instanceof-pattern
class InstanceofPattern {
    boolean m(Object value) { return value instanceof @U("pattern") String text; }
}


// invocation-type-argument
class InvocationTypeArgument {
    <T> T id(T value) { return value; } String m() { return this.<@U("argument") String>id("x"); }
}


// jspecify-between-modifiers
class JspecifyBetweenModifiers {
    public @org.jspecify.annotations.Nullable static String m() { return null; }
}


// jspecify-return-inline
class JspecifyReturnInline {
    public @org.jspecify.annotations.Nullable String m() { return null; }
}


// jspecify-return-next-line
class JspecifyReturnNextLine {
    public
    @org.jspecify.annotations.Nullable String m() { return null; }
}


// lambda-typed-parameter
class LambdaTypedParameter {
    Function<String, String> f = (@D("declaration") final @U("type") String value) -> value;
}


// lambda-var-parameter
class LambdaVarParameter {
    Function<String, String> f = (@U("type") var value) -> value;
}


// lambda-var-without-annotation
class LambdaVarWithoutAnnotation {
    Function<String, String> f = (var value) -> value;
}


// last-modifier-next-line
class LastModifierNextLine {
    public
    static @U("return") String m() { return null; }
}


// local-array-dimensions
class LocalArrayDimensions {
    void m() { String @U("common") [] a = null, b @U("extra") [] = null; }
}


// local-variable
class LocalVariable {
    void m() { @D("local") final @U("type") String value = "x"; }
}


// method-reference-type-argument
class MethodReferenceTypeArgument {
    static <T> T id(T value) { return value; } Function<String, String> f = MethodReferenceTypeArgument::<@U("argument") String>id;
}


// method-type-parameters-multiline
class MethodTypeParametersMultiline {
    public <
    @P("T") T extends @U("bound") Number,
    @P("V") V> V m(T number, V value) { return value; }
}


// multi-catch
class MultiCatch {
    void m() { try { throw new IOException(); } catch (@U("io") IOException | @U("argument") IllegalArgumentException failure) {} }
}


// new-array
class NewArray {
    Object m() { return new @U("element") String @U("first") [1] @U("second") [2]; }
}


// new-expression
class NewExpression {
    Object m() { return new @U("new") ArrayList<@U("argument") String>(); }
}


// no-modifier
class NoModifier {
    @U("return") String m() { return null; }
}


// normal-annotation-between-modifiers
class NormalAnnotationBetweenModifiers {
    public @D(value = "method") static String m() { return null; }
}


// override-between-modifiers
class OverrideBetweenModifiers {
    public @Override final String toString() { return "x"; }
}


// parameter-arrays
class ParameterArrays {
    void m(@U("element") String @U("outer") [] @U("inner") [] value) {}
}


// parameter-arrays-after-name
class ParameterArraysAfterName {
    void m(@U("element") String value @U("outer") [] @U("inner") []) {}
}


// parameter-declaration-and-type
class ParameterDeclarationAndType {
    void m(@D("declaration") final @U("type") String value) {}
}


// parameter-dual-target
class ParameterDualTarget {
    void m(final @B("both") String value) {}
}


// parameter-primitive
class ParameterPrimitive {
    void m(final @U("primitive") int value) {}
}


// parameter-type-after-final
class ParameterTypeAfterFinal {
    void m(final @U("type") String value) {}
}


// parameter-type-arguments
class ParameterTypeArguments {
    void m(@U("list") List<@U("argument") ? extends @U("bound") Number> values) {}
}


// parameter-type-before-final
class ParameterTypeBeforeFinal {
    void m(@U("type") final String value) {}
}


// parameter-type-next-line
class ParameterTypeNextLine {
    void m(final
    @U("type") String value) {}
}


// primitive-return-inline
class PrimitiveReturnInline {
    public @U("primitive") int m() { return 0; }
}


// primitive-return-next-line
class PrimitiveReturnNextLine {
    public
    @U("primitive") int m() { return 0; }
}


// qualified-inner-type
class QualifiedInnerTypeOuter { class Inner {} } class QualifiedInnerType { QualifiedInnerTypeOuter.@U("inner") Inner value; }

// qualified-owner-and-inner
class QualifiedOwnerAndInnerOuter { class Inner {} } class QualifiedOwnerAndInner { @U("outer") QualifiedOwnerAndInnerOuter.@U("inner") Inner value; }

// qualified-return
class QualifiedReturn {
    public java.lang.@U("return") String m() { return null; }
}


// receiver
class Receiver {
    void m(@U("receiver") Receiver this, @U("parameter") String value) {}
}


// record-compact-constructor
record RecordCompactConstructor(@U("component") String value) { @D("constructor") RecordCompactConstructor {} }

// record-components
record RecordComponents(@D("declaration") @U("list") List<@U("element") String> values, @U("element") String @U("array") [] array) {}

// record-type-parameter
record RecordTypeParameter<@P("T") T>(@U("component") T value) {}

// repeatable-declaration-duplication
class RepeatableDeclarationDuplication {
    @M("a") @M("b")
    public String m() { return null; }
}


// repeatable-return
class RepeatableReturn {
    public @R("a") @R("b") String m() { return null; }
}


// repeatable-type-use-duplication
class RepeatableTypeUseDuplication {
    @R("a") @R("b")
    public String m() { return null; }
}


// reported
class Reported {
    @Deprecated @SuppressWarnings({"unchecked", "rawtypes"})
    public @U("return") String m() { return null; }
}


// return-next-line
class ReturnNextLine {
    public
    @U("return") String m() { return null; }
}


// return-next-line-deep-indent
class ReturnNextLineDeepIndent {
    public
              @U("return") String m() { return null; }
}


// safe-varargs-between-modifiers
class SafeVarargsBetweenModifiers {
    public @SafeVarargs static <T> List<T> m(T... values) { return Arrays.asList(values); }
}


// throws-types
class ThrowsTypes {
    void m() throws @U("io") IOException, @U("state") IllegalStateException {}
}


// try-resource
class TryResource {
    void m() throws IOException { try (@U("resource") ByteArrayInputStream in = new ByteArrayInputStream(new byte[0])) {} }
}


// type-use-between-modifiers
class TypeUseBetweenModifiers {
    public @U("return") static String m() { return null; }
}


// varargs
class Varargs {
    void m(@U("element") String @U("varargs") ... values) {}
}


// void-declaration-between-modifiers
class VoidDeclarationBetweenModifiers {
    public @D("method") static void m() {}
}

class GenericDualTargetBeforeTypeParameters {
    public @B("both") <T> T m(T value) { return value; }
}

class GenericDualTargetAfterTypeParameters {
    public <T> @B("both") T m(T value) { return value; }
}

class IdenticalRepeatableAnnotationsAcrossModifier {
    @R("same") public @R("same") String m() { return null; }
}

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE_USE)
@interface U { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD,
    ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE, ElementType.RECORD_COMPONENT})
@interface D { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE_PARAMETER)
@interface P { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.FIELD,
    ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.TYPE_PARAMETER})
@interface B { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE_USE) @Repeatable(Rs.class)
@interface R { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE_USE)
@interface Rs { R[] value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @Repeatable(Ms.class)
@interface M { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface Ms { M[] value(); }

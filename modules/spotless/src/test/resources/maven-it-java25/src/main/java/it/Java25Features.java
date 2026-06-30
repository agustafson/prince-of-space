package it;
import java.util.*;import java.util.function.*;import java.util.stream.*;
/** Deliberately mis-formatted file exercising Java 25 language features (NON-preview). */
@SuppressWarnings({"unused","unchecked"})
public class Java25Features{

// ------- enhanced enum with abstract method per constant -------
enum Planet{
MERCURY(3.303e+23,2.4397e6){@Override public double gravity(){return G*mass/radius/radius;}},
VENUS(4.869e+24,6.0518e6){@Override public double gravity(){return G*mass/radius/radius;}};
private static final double G=6.673e-11;
final double mass;final double radius;
Planet(double mass,double radius){this.mass=mass;this.radius=radius;}
public abstract double gravity();
}

// ------- sealed interface + permits + inner records -------
sealed interface Shape permits Shape.Circle,Shape.Rect,Shape.Tri{
double area();
record Circle(double radius) implements Shape{@Override public double area(){return Math.PI*radius*radius;}}
record Rect(double w,double h) implements Shape{@Override public double area(){return w*h;}}
record Tri(double b,double h) implements Shape{@Override public double area(){return 0.5*b*h;}}
}

// ------- generic record -------
record Pair<A,B>(A first,B second){}

// ------- nested generics field -------
Map<String,List<? extends Number>> table=new HashMap<>();

// ------- main feature showcase method -------
void features(){
// var + ArrayList
var list=new ArrayList<String>();list.add("hello");list.add("world");

// deliberately mis-spaced assignment to confirm reformatting
int x=1;System.out.println(x);

// lambda + method reference + bounded wildcard
Comparator<String> cmp=(a,b)->Integer.compare(a.length(),b.length());
list.sort(String::compareTo);

// text block
String json="""
{"name":"test","value":42}
""";

// pattern-matching instanceof
Object obj="hello world";
if(obj instanceof String s){System.out.println(s.toUpperCase());}

// switch expression (arrow form)
int day=3;
String dayName=switch(day){case 1->"Mon";case 2->"Tue";case 3->"Wed";default->"Other";};

// record patterns in switch + when guards
Shape shape=new Shape.Circle(5.0);
String sizeLabel=switch(shape){
case Shape.Circle(var r) when r>100->"large circle";
case Shape.Circle(var r) when r>10->"medium circle";
case Shape.Circle c->"small circle";
case Shape.Rect(var w,var h) when w==h->"square:"+w;
case Shape.Rect r->"rect:"+r.w()+"x"+r.h();
case Shape.Tri(var b,var h)->"tri:"+b+"x"+h;
};

// switch expression with type patterns
double area=switch(shape){
case Shape.Circle(var r)->Math.PI*r*r;
case Shape.Rect(var w,var h)->w*h;
case Shape.Tri(var b,var h)->0.5*b*h;
};

// unnamed variable _ (Java 22, non-preview)
try{int parsed=Integer.parseInt("not-a-number");}catch(NumberFormatException _){System.out.println("parse failed");}

// stream with method refs
var upper=list.stream().filter(s->s.length()>3).map(String::toUpperCase).collect(Collectors.toList());

// bounded wildcards + stream
List<? extends Number> nums=List.of(1,2,3);
double sum=nums.stream().mapToDouble(Number::doubleValue).sum();

// ternary expression
String label=list.isEmpty()?"none":list.getFirst();

// local record (Java 16+, non-preview)
record TaggedValue(String tag,int value){String describe(){return tag+":"+value;}}
var tv=new TaggedValue("score",42);
System.out.println(tv.describe());
}

// ------- method using bounded wildcards and generics -------
<T extends Comparable<T>> List<T> sorted(List<? extends T> input){
return input.stream().map(x->x).sorted().collect(Collectors.toList());
}

// ------- functional interface field + lambda returning lambda -------
Function<String,Function<String,String>> curried=prefix->suffix->prefix+":"+suffix;
}

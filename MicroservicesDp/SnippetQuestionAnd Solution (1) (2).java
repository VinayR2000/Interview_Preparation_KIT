/***********************************************************************************/
public class Test {
    public static void main(String[] args) {
        A a = new B();
        a.disp();
    }
}
class A {
    public void disp() {
        System.out.println("class A");
    }
}
class B extends A {
    protected void disp() {
        System.out.println("class B");
    }
}

compilation error : disp()' in 'B' clashes with 'disp()' in 'A'; attempting to assign weaker access privileges ('protected'); was 'public'
/***********************************************************************************/
public class Test {
    public static void main(String[] args) {
        try {
            A a = new B();
            a.disp();
        } catch (Exception exception ) {
      // Handle Exception
        }
    }
}
class A {
    public void disp() throws Exception {
        System.out.println("class A");
    }
}
class B extends A {
    public void disp() throws IOException {
        System.out.println("class B");
    }
}
Works and prints class B
/***********************************************************************************/
public class Test {
    public static void main(String[] args) {
        try {
            A a = new B();
            a.disp();
        } catch (Exception exception ) {
      // Handle Exception
        }
    }
}
class A {
    public void disp() {
        System.out.println("class A");
    }
}
class B extends A {
    public void disp() throws IOException {
        System.out.println("class B");
    }
}
Complier Error : 'disp()' in 'B' clashes with 'disp()' in 'A'; overridden method does not throw 'java.io.IOException
/***********************************************************************************/
public class Test {
    public static void main(String[] args) {
        try {
            A a = new B();
            a.disp();
        } catch (Exception exception ) {
      // Handle Exception
        }
    }
}
class A {
    public void disp() {
        System.out.println("class A");
    }
}
class B extends A {
    public void disp() throws RuntimeException {
        System.out.println("class B");
    }
}
Works and prints class B
/***********************************************************************************/
public class JavaClass {
    public void disp(String a) {
        System.out.println("test A");
    }
    public void disp(Object b) {
        System.out.println("test B");
    }
    public static void main(String[] args) {
        new JavaClass().disp(null);
    }
}
Prints Test A : null try to match closest type that is String, Object will be generic type 
/***********************************************************************************/
public class JavaClass {
    public void disp(long a, int b) {
        System.out.println("test A");
    }
    public void disp(int b, long a) {
        System.out.println("test B");
    }
    public static void main(String[] args) {
// TODO Auto-generated method stub
        new JavaClass().disp(2, 2);
    }
}
Compiler error: Ambigous overloading
/***********************************************************************************/
public class JavaClass {
    public static void main(String[] args) {
        A a = new B();
        a.disp();
    }
}
class A {
    public static void disp() {
        System.out.println("in class A");
    }
}
class B extends A {
    public static void disp() {
        System.out.println("in class B");
    }
}
Static is Class level scope prints in class A
/***********************************************************************************/
public class JavaClass {
    public static void main(String[] args) {
        A a = new B();
        a.disp();
    }
}
class A {
    public void disp() {
        System.out.println("in class A");
    }
}
class B extends A {
    public void disp() {
        System.out.println("in class B");
    }
}

works fine in class B genric inheritence example
/***********************************************************************************/

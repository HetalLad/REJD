// Greeting.java
public class Greeting {
    public static void main(String[] args) {
        // Check if a command-line argument was provided
        if (args.length > 0) {
            String name = args[0];
            System.out.println("Hi, " + name + "!");
        } else {
            System.out.println("Hello, stranger. Please provide a name as a command-line argument.");
        }
    }
}


package ru.trolsoft.asmext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: avr-asm-ext: <source file> <output file>");
            System.exit(1);
        }
        String src = args[0];
        String out = args[1];
        Parser parser = new Parser();
        File srcFile = new File(src);
        if (!srcFile.exists()) {
            System.out.println("File not found: " + src);
            System.exit(2);
        }
        try {
            parser.parse(srcFile);
            try (FileWriter writer = new FileWriter(out)) {
                for (String s : parser.getOutput()) {
                    writer.write(s);
                    writer.write('\n');
                }
            }
        } catch (IOException e1) {
            System.out.println(e1.getMessage());
        } catch (SyntaxException e2) {
            System.out.println("ERROR: " + src + ":" + e2.line + " " + e2.getMessage());
        }
    }
}

package ru.trolsoft.asmext.files;

import ru.trolsoft.asmext.processor.Token;
import ru.trolsoft.asmext.utils.TokenString;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class OutputFile {
    private final List<String> lines = new ArrayList<>();
    private StringBuilder lastLine;

    public void writeToFile(String fileName) throws IOException {
        try (FileWriter writer = new FileWriter(fileName)) {
            write(writer);
        }
    }

    public void write(Writer writer) throws IOException {
        addLastLine();
        for (String s : lines) {
            writer.write(s);
            writer.write('\n');
        }
    }


    private void addLastLine() {
        if (lastLine != null) {
            lines.add(lastLine.toString());
            lastLine = null;
        }
    }

    public void add(String line) {
        addLastLine();
        lines.add(line);
    }

    public void add(TokenString line) {
        addLastLine();
        startNewLine();
        lastLine.append(line.getIndent());
        for (String s : line) {
            lastLine.append(s);
        }
        if (line.hasComment()) {
            lastLine.append(line.getComment());
        }
    }

    public void add(StringBuilder line) {
        addLastLine();
        lines.add(line.toString());
    }

    public void add(String[] strings) {
        startNewLine();
        for (String s : strings) {
            lastLine.append(s);
        }
    }

    public StringBuilder startNewLine() {
        addLastLine();
        lastLine = new StringBuilder();
        return lastLine;
    }

    public StringBuilder startLineWitchSpacesFrom(String str) {
        startNewLine();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == ' ' || c == '\t') {
                lastLine.append(c);
            } else {
                break;
            }
        }
        return lastLine;
    }


    public int size() {
        return lines.size() + (lastLine == null ? 0 : 1);
    }

    public boolean isEmpty() {
        return lines.isEmpty() && lastLine == null;
    }

    public String get(int index) {
        if (index < lines.size()) {
            return lines.get(index);
        } else if (index == lines.size() && lastLine != null) {
            return lastLine.toString();
        }
        throw new IndexOutOfBoundsException("index: " + index + ", size: " + size());
    }


//    public String getLast() {
//        return get(size()-1);
//    }

    public void clear() {
        lines.clear();
        lastLine = null;
    }

    private StringBuilder appendCommand(String indent, String cmd, String arg1, String arg2) {
        return startNewLine().append(indent).append(cmd).append('\t').append(arg1).append(", ").append(arg2);
    }

    private StringBuilder appendCommand(String indent, String cmd, String arg) {
        startNewLine().append(indent).append(cmd);
        if (arg != null) {
            lastLine.append('\t').append(arg);
        }
        return lastLine;
    }

    public StringBuilder appendCommand(TokenString src, String cmd, String arg1, String arg2) {
        StringBuilder sb = appendCommand(src.getIndent(), cmd, arg1, arg2);
        if (src.hasComment()) {
            sb.append("\t\t").append(src.getComment());
        }
        return sb;
    }

    public StringBuilder appendCommand(TokenString src, String cmd, String arg) {
        StringBuilder sb = appendCommand(src.getIndent(), cmd, arg);
        if (src.hasComment()) {
            sb.append("\t\t").append(src.getComment());
        }
        return sb;
    }

    public StringBuilder appendCommand(TokenString src, String cmd, Token arg1, Token arg2) {
        return appendCommand(src, cmd, arg1.asString(), arg2.asString());
    }

    public StringBuilder appendCommand(TokenString src, String cmd, Token arg1, String arg2) {
        return appendCommand(src, cmd, arg1.asString(), arg2);
    }

    public StringBuilder appendCommand(TokenString src, String cmd, Token arg) {
        return appendCommand(src, cmd, arg.asString());
    }

    public StringBuilder appendCommand(TokenString src, String cmd) {
        return appendCommand(src, cmd, (String) null);
    }

    public void addComment(String s) {
        startNewLine().append("; ").append(s);
        addLastLine();
    }

    public void addComment(TokenString s) {
        addComment(s.toString());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String s : lines) {
            sb.append(s).append('\n');
        }
        if (lastLine != null) {
            sb.append(lastLine);
        }
        return sb.toString();
    }


    public StringBuilder getLastLineBuilder() {
        return lastLine;
    }


    public String getLastLine() {
        if (lastLine != null) {
            return lastLine.toString();
        }
        if (lines.isEmpty()) {
            return null;
        }
        return lines.get(lines.size()-1);
    }
}

package ru.trolsoft.asmext.files;

import ru.trolsoft.asmext.utils.TokenString;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public class SourceFile implements Iterable<TokenString> {

    private final List<TokenString> lines = new ArrayList<>();

    public SourceFile() {

    }

    public void read(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            read(reader);
        }
    }

    public void read(BufferedReader reader) throws IOException {
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            lines.add(new TokenString(line));
        }
    }

    public List<TokenString> getLines() {
        return lines;
    }

    @Override
    public Iterator<TokenString> iterator() {
        return lines.iterator();
    }

    @Override
    public void forEach(Consumer<? super TokenString> action) {
        lines.forEach(action);
    }

    @Override
    public Spliterator<TokenString> spliterator() {
        return lines.spliterator();
    }
}

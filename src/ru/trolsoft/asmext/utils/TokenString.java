package ru.trolsoft.asmext.utils;


import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class TokenString implements Comparable<TokenString>, CharSequence, Iterable<String> {
    private final String string;
    private List<String> tokens;
    private String pure;
    private String indent;
    private String comment;

    public TokenString(String string) {
        this.string = string;
    }

    public TokenString(String[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (String s : tokens) {
            sb.append(s);
        }
        this.string = sb.toString();
    }

    public List<String> getTokens() {
        if (tokens == null) {
            tokens = splitToTokensList(string);
            saveAndRemoveIndent();
            saveAndRemoveComment();
        }
        return tokens;
    }


    @Override
    public int length() {
        return string.length();
    }

    @Override
    public char charAt(int index) {
        return string.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return string.subSequence(start, end);
    }

    @Override
    public IntStream chars() {
        return string.chars();
    }

    @Override
    public IntStream codePoints() {
        return string.codePoints();
    }

    @Override
    public int compareTo(TokenString o) {
        return string.compareTo(o.string);
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof TokenString) {
            TokenString ts = (TokenString)anObject;
            return string.equals(ts.string);
        }
        return string.equals(anObject);
    }

    @Override
    public int hashCode() {
        return string.hashCode();
    }

    @Override
    public Iterator<String> iterator() {
        return getTokens().iterator();
    }

    @Override
    public void forEach(Consumer<? super String> action) {
        getTokens().forEach(action);
    }

    @Override
    public Spliterator<String> spliterator() {
        return getTokens().spliterator();
    }

    public int size() {
        return getTokens().size();
    }

    public boolean isEmpty() {
        return getTokens().isEmpty();
    }

    public String getToken(int i) {
        return getTokens().get(i);
    }

    public String modifyToken(int i, String nevVal) {
        return getTokens().set(i, nevVal);
    }

    @Override
    public String toString() {
        return string;
    }

    public String toLowerCase(Locale locale) {
        return string.toLowerCase(locale);
    }

    public String toLowerCase() {
        return string.toLowerCase();
    }

    public String toUpperCase(Locale locale) {
        return string.toUpperCase(locale);
    }

    public String toUpperCase() {
        return string.toUpperCase();
    }

    public boolean contains(CharSequence s) {
        return string.contains(s);
    }

    public boolean startsWith(String prefix) {
        return string.startsWith(prefix);
    }

    public boolean endsWith(String suffix) {
        return string.endsWith(suffix);
    }

    public String pure() {
        if (pure == null) {
            pure = removeCommentsAndSpaces(string);
        }
        return pure;
    }

    private String removeCommentsAndSpaces(String s) {
        // TODO
        s = s.trim();
        int index = s.indexOf(';');
        if (index >= 0) {
            s = s.substring(0, index).trim();
        }
        index = s.indexOf("//");
        if (index >= 0) {
            s = s.substring(0, index).trim();
        }
        return s;
    }

    static List<String> splitToTokensList(String line) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        final String delim = " \t,.+-*/=():;<>!";
        StringTokenizer tokenizer = new StringTokenizer(line, delim, true);
        boolean commentStarted = false;
        while (tokenizer.hasMoreElements()) {
            String next = tokenizer.nextToken();
            String prev = result.isEmpty() ? null : result.get(result.size()-1);
            if (prev != null && next.trim().isEmpty() && prev.trim().isEmpty()) {
                result.set(result.size()-1, prev + next);
                continue;
            }

            // ' ' and '\t' is char const
            if ("'".equals(prev) && delim.contains(next) && next.length() == 1) {
                result.set(result.size()-1, prev + next);
                continue;
            }
            if (("'".equals(next)) && (prev != null && prev.startsWith("'") && prev.length() == 2) && delim.contains(prev.substring(1))) {
                result.set(result.size()-1, prev + next);
                continue;
            }

            if (commentStarted) {
                result.set(result.size()-1, prev + next);
                continue;
            }
            if (";".equals(next)) {
                commentStarted = true;
            } else if ("/".equals(next) && "/".equals(prev)) {
                result.set(result.size()-1, prev + next);
                commentStarted = true;
                continue;
            }

            if ("=".equals(next)) {
                if ("=".equals(prev) || "!".equals(prev) || ">".equals(prev) || "<".equals(prev) || "+".equals(prev) || "-".equals(prev)) {
                    result.set(result.size()-1, prev + next);
                    continue;
                }
            } else if ((">".equals(next) && ">".equals(prev)) || ("<".equals(next) && "<".equals(prev))) {
                result.set(result.size()-1, prev + next);
                continue;
            } else if (("+".equals(next) && "+".equals(prev)) || ("-".equals(next) && "-".equals(prev))) {
                result.set(result.size()-1, prev + next);
                continue;
            }

            result.add(next);
        }
        return result;
    }

    private void saveAndRemoveIndent() {
        if (tokens.isEmpty()) {
            indent = "";
            return;
        }
        String firstToken = tokens.get(0);
        if (firstToken.trim().isEmpty()) {
            indent = firstToken;
            tokens.remove(0);
        } else {
            indent = "";
        }
    }

    private void saveAndRemoveComment() {
        if (tokens.isEmpty()) {
            comment = "";
            return;
        }
        String lastToken = getLastToken();
        if (isComment(lastToken)) {
            comment = lastToken;
            tokens.remove(tokens.size()-1);
        } else {
            comment = "";
        }
    }

    public void removeEmptyTokens() {
        getTokens().removeIf(s -> s == null || s.trim().isEmpty());
    }

    public String getIndent() {
        if (indent == null) {
            getTokens();
        }
        return indent;
    }

    public String getComment() {
        if (comment == null) {
            getTokens();
        }
        return comment;
    }

    public boolean hasComment() {
        return !getComment().isEmpty();
    }


    public String getFirstToken() {
        return getTokens().isEmpty() ? null : tokens.get(0);
    }

    public String getLastToken() {
        return getTokens().isEmpty() ? null : tokens.get(tokens.size()-1);
    }


    private static boolean isComment(String s) {
        return s.startsWith(";") || s.startsWith("//");
    }

    public void removeFirstToken() {
        getTokens().remove(0);
    }

    public void removeLastToken() {
        getTokens().remove(getTokens().size()-1);
    }

    public boolean firstTokenIs(String s) {
        return s.equals(getFirstToken());
    }

    public boolean lastTokenIs(String s) {
        return s.equals(getLastToken());
    }

    public String mergeTokens(int fromIndex) {
        StringBuilder sb = new StringBuilder();
        while (fromIndex < tokens.size()) {
            sb.append(tokens.get(fromIndex++));
        }
        return sb.toString();
    }
}

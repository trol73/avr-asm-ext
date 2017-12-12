package ru.trolsoft.asmext.data;

public class NamedPair {
    public final String name;
    public final String value;

    public NamedPair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public int hashCode() {
        return 31*name.hashCode() + value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NamedPair)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        NamedPair a = (NamedPair) obj;
        return a.name.equals(name) && a.value.equals(value);
    }


    @Override
    public String toString() {
        return "(" + name + " = " + value + ")";
    }
}

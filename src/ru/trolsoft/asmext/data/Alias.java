package ru.trolsoft.asmext.data;

public class Alias {
    public final String name;
    public final String register;

    public Alias(String name, String register) {
        this.name = name;
        this.register = register;
    }

    @Override
    public int hashCode() {
        return 31*name.hashCode() + register.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Alias)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        Alias a = (Alias)obj;
        return a.name.equals(name) && a.register.equals(register);
    }

    @Override
    public String toString() {
        return "alias (" + name + " -> " + register +")";
    }
}

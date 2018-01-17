package ru.trolsoft.asmext.data;

import ru.trolsoft.asmext.processor.Expression;

public class Argument {
    public final String name;
    public final Expression expr;

    public Argument(String name, Expression expr) {
        this.name = name;
        this.expr = expr;
    }

    public Argument(String name) {
        this.name = name;
        this.expr = new Expression();
    }

//    @Override
//    public int hashCode() {
//        return 31*name.hashCode() + value.hashCode();
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (!(obj instanceof Argument)) {
//            return false;
//        }
//        if (obj == this) {
//            return true;
//        }
//        Argument a = (Argument) obj;
//        return a.name.equals(name) && a.value.equals(value);
//    }
//
//
//    @Override
//    public String toString() {
//        return "(" + name + " = " + value + ")";
//    }
}

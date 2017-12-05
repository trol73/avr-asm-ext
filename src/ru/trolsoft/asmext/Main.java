package ru.trolsoft.asmext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
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
            System.exit(1);
        } catch (SyntaxException e2) {
            System.out.println(src + ":" + e2.line + ": Error: " + e2.getMessage());
            System.exit(1);
        }
        //System.out.println("Elapsed time: " + (System.currentTimeMillis() - t0) + " ms");
    }
}
/*
.equ    вложенные константы
макросы

.MACRO swap_regs
    push    $1
    $1 = $0
    pop     $0
.ENDMACRO

.MACRO SET_BAT
.IF  $0 > 0x3F
.MESSAGE "Адрес больше, чем 0x3f"
lds $2, @0
sbr $2, (1<<$1)
sts $0, $2
.ELSE
.MESSAGE " Адрес меньше или равен 0x3f"
.ENDIF
.ENDMACRO


.EQU - установить постоянное выражение
    Директива EQU присваивает метке значение. Эта метка может позднее использоваться в выражениях.
     Метка, которой присвоено значение данной директивой, не может быть переназначена и её значение не может быть изменено.
Синтаксис:
.EQU метка = выражение
Пример:
.EQU io_offset = 0x23
.EQU porta = io_offset + 2


 IF (reg < 32) goto @1
 IF (reg < 32):
    goto @1
 ENDIF

 IF (sreg.s)
 ENDIF

 IF (!sreg.z)
 ENDIF
                                                                unsigned            signed

 if (r1 == r2) goto lbl;        cp r1, r2; breq lbl

 if (r1 != r2) goto lbl;        cp r1, r2; brne lbl

 if (r1 == const) goto lbl;     cpi r, const; breq lbl

 if (r != const) goto lbl;     cpi r, const; brne lbl

 if (r1 < r2) goto lbl;         cp r1, r2;                         brlo               brlt

 if (r1 > r2) goto lbl;         cp r1, r2

 if (r1 <= r2) goto lbl;        cp r1, r2

 if (r1 >= r2) goto lbl;        cp r1, r2;                          brsh            brge

 if (r == 0) goto lbl           tst r; breq lbl

 if (r != 0) goto lbl           tst r; brne lbl

.if
.else
.endif

 */
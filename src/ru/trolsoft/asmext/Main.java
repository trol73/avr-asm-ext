package ru.trolsoft.asmext;

import java.io.File;
import java.io.IOException;
import ru.trolsoft.asmext.processor.Parser;
import ru.trolsoft.asmext.processor.SyntaxException;

public class Main {

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
        if (args.length != 2) {
            System.out.println("Usage: avr-asm-ext: <source file> <output file>");
            System.exit(1);
        }
        String src = args[0];
        String out = args[1];
        Parser parser = new Parser(src.toLowerCase().endsWith(".s"));
        File srcFile = new File(src);
        if (!srcFile.exists()) {
            System.out.println("File not found: " + src);
            System.exit(2);
        }
        try {
            parser.parse(srcFile);
            parser.getOutput().writeToFile(out);
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

	rmp = ' '
	R0 = rmp
	r0 = rmp = ' '

	rmp = 4 ; 1 seconds
	sUartMonFCnt = rmp
	sUartMonFCnt = rmp = 4


	rmp = BYTE1(100000000) ; check overflow
	cp rRes1, rmp
	rmp = BYTE2(100000000)
	cpc rRes2, rmp
	rmp = BYTE3(100000000)
	cpc rRes3, rmp
	rmp = BYTE4(100000000)
	cpc rRes4, rmp
	brcs @1



if (r2.r1 < ZH.ZL) goto @2 ; ended subtraction

    .EQU cDecSep = '.' 		; decimal separator for numbers displayed
	ldi	rmp, cDecSep
	;rmp = cDecSep

	.loop (rmp = 16)
	UartMonF1:
		sbis UCSRA, UDRE ; wait for empty buffer
		rjmp UartMonF1                              continue !!!
		ld R0,Z+
		out UDR,R0
	.endloop

	X = s_video_mem + 16
	;ldi	XH, HIGH(s_video_mem+16)
	;ldi	XL, LOW(s_video_mem+16)

	if (ZL == 1) goto Interval_enc_clockwise
	if (ZL == 7) goto Interval_enc_clockwise
	if (ZL == 8) goto Interval_enc_clockwise
	if (ZL == 14) goto Interval_enc_clockwise


	; rmp = $data[Z++]
	ld rmp, Z+ ; read next char



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



 IF (reg < 32) goto @1
 IF (reg < 32):
    goto @1
 ENDIF

 IF (sreg.s)
 ENDIF

 IF (!sreg.z)
 ENDIF
                                                                unsigned            signed

.if
.else
.endif

 */
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

	if (rmp == cCR) goto @NoPar
	if (rmp == cLF) goto @NoPar
        if (rmp == cCR || rmp == cLF) goto @NoPar

    sbi	_SFR_IO_ADDR(UCSRB), UDRIE			; enable UDRE interrupt
        $port[UCSRB].UDRIE = 1


.args v(ZH.ZL)
.args v(Z)
.use rRes3.rRes2.rRes1 as res

	rmp = BYTE1(100000000) ; check overflow
	cp rRes1, rmp
	rmp = BYTE2(100000000)
	cpc rRes2, rmp
	rmp = BYTE3(100000000)
	cpc rRes3, rmp
	rmp = BYTE4(100000000)
	cpc rRes4, rmp
	brcs @1

	ZH.ZL.rmp = 100000	; 100 k
	rcall DisplDecX3
	Z = 10000			; 10 k
	rcall DisplDecX2
	Z = 1000			; 1 k
	rcall DisplDecX2


st X+, rmp
        $mem[X++] = rmp
in	rmp, PINC
        rmp = $io[PINC]
out	OCR2, rmp
        $io[OCR2] = rmp
ld rRes4, Z+
        rRes4 = $mem[Z++]

	ld rRes1, Z+ ; copy counter value
	ld rRes2, Z+
	ld rRes3, Z+
	ld rRes4, Z+
	    (rRes1, rRes2, rRes3, rRes4) = $mem[Z++
	    (rRes1, rRes2, rRes3, rRes4) <- $mem[Z++]

	rmp = ' '
	st X+,rmp
	rmp = 'H'
	st X+,rmp
	rmp = 'z'
	st X+,rmp
	rmp = ' '
	st X,rmp
	        $ram[X++] = rmp(' ', 'H', 'z')
	        $ram[X] = rmp = ' '

		lpm
		Z ++

		st X+, R0
		    $mem[X++] = r0 = $prg[Z++]






	st Z+, rRes1 ; copy counter value
	st Z+, rRes2
	st Z+, rRes3
	st Z+, rRes4
	        $mem[Z++] <- rRes1, rRes2, rRes3, rRes4
	        $mem[Z++] = (rRes1, rRes2, rRes3, rRes4)

	rmp = ' '
	st	X+, rmp
	        $mem[X++] = rmp = ' '
	        rmp = ' ' -> $mem[X++]

	rmp = '0' + R2
	st	X+, rmp
	    $mem[X++] = '0' + R2




	if (rDiv1 == 0) goto CycleM0a ; no error
	rjmp CycleOvf



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
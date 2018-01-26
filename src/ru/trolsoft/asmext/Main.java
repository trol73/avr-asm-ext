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

io[UCSRC] = rmp = (1<<URSEL)|(1<<UCSZ1)|(1<<UCSZ0) ; set 8 bit characters
io[UCSRC] = rmp = bitmask(URSEL, UCSZ1, UCSZ0)

brcs CalcPwO
    if (F_CARRY) goto CalcPwO

flags[CARRY]
    if (!sreg.CARRY) goto CalcPwO

clc
    sreg.CARRY = 0
sec
    sreg.CARRY = 1

	rmp = LOW(1001)
	cp rRes1, rmp
	rmp = HIGH(1001)
	cpc rRes2, rmp
	brcc @E


	rmp = BYTE1(100000000) ; check overflow
	cp rRes1, rmp
	rmp = BYTE2(100000000)
	cpc rRes2, rmp
	rmp = BYTE3(100000000)
	cpc rRes3, rmp
	rmp = BYTE4(100000000)
	cpc rRes4, rmp
	brcs @1

adc r0, rmp
    r0 += sreg.CARRY + rmp


GLOBAL_INT
Bit 7 - I: Global Interrupt Enable - Разрешение глобального прерывания.
Бит разрешения глобального прерывания для разрешения прерывания должен быть установлен в состояние 1. Управление разрешением конкретного прерывания выполняется регистрами маски прерывания GIMSK и TIMSK. Если бит глобального прерывания очищен (в состоянии 0), то ни одно из разрешений конкретных прерываний, установленных в регистрах GIMSK и TIMSK, не действует. Бит I аппаратно очищается после прерывания и устанавливается для последующего разрешения глобального прерывания командой RETI.

BIT_COPY
Bit 6 - T: Bit Copy Storage - Бит сохранения копии.
Команды копирования бита BLD (Bit LoaD) и BST (Bit STore) используют бит T как бит источник и бит назначения при операциях с битами. Командой BST бит регистра регистрового файла копируется в бит T, командой BLD бит T копируется в регистр регистрового файла.

HALF_CARRY
Bit 5 - H: Half Carry Flag - Флаг полупереноса
Флаг полупереноса указывает на полуперенос в ряде арифметических операций. Более подробная информация приведена в описании системы команд.

SIGN
Bit 4 - S: Sign Bit, S = N V - Бит знака
Бит S всегда находится в состоянии, определяемом логическим исключающим ИЛИ (exclusive OR) между флагом отрицательного значения N и дополнением до двух флага переполнения V. Более подробная информация приведена в описании системы команд.

TCO
Bit 3 - V: Two’s Complement Overflow Flag - Дополнение до двух флага переполнения
Дополнение до двух флага V поддерживает арифметику дополнения до двух. Более подробная информация приведена в описании системы команд.

NEG
Bit 2 - N: Negative Flag - Флаг отрицательного значения
Флаг отрицательного значения N указывает на отрицательный результат ряда арифметических и логических операций. Более подробная информация приведена в описании системы команд.

ZERO
Bit 1 - Z: Zero Flag - Флаг нулевого значения
Флаг нулевого значения Z указывает на нулевой результат ряда арифметических и логических операций. Более подробная информация приведена в описании системы команд.

CARRY
Bit 0 - C: Carry Flag - Флаг переноса
Флаг переноса C указывает на перенос в арифметических и логических операциях. Более подробная информация приведена в описании системы команд.




    rmp |= 1 << bLcdRs 	; set Rs to one
        rmp[bLcdRs] = 1

	if (rmp == cCR) goto @NoPar
	if (rmp == cLF) goto @NoPar
        if (rmp == cCR || rmp == cLF) goto @NoPar


    sbrs rFlg,bEdge
    rjmp CycleM5a

        if (!io[rFlg].bEdge) rjmp CycleM5a


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



	if (rDiv1 == 0) goto CycleM0a ; no error
	rjmp CycleOvf



if (r2.r1 < ZH.ZL) goto @2 ; ended subtraction


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
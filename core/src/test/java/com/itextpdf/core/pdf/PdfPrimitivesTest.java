package com.itextpdf.core.pdf;

import com.itextpdf.basics.PdfException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public class PdfPrimitivesTest {

    static final String destinationFolder = "./target/test/com/itextpdf/core/pdf/PdfPrimitivesTest/";
    static final PdfName TestArray = new PdfName("TestArray");
    static final int DefaultArraySize = 64;
    static final int PageCount = 30000;

    public static class RandomString {

        private static final char[] symbols;
        private final Random random = new Random();
        private final char[] buf;

        static {
            StringBuilder tmp = new StringBuilder();
            for (char ch = 'A'; ch <= 'Z'; ++ch)
                tmp.append(ch);
            for (char ch = 'a'; ch <= 'z'; ++ch)
                tmp.append(ch);
            for (char ch = '0'; ch <= '9'; ++ch)
                tmp.append(ch);
            symbols = tmp.toString().toCharArray();
        }

        public RandomString(int length) {
            if (length < 1)
                throw new IllegalArgumentException("length < 1: " + length);
            buf = new char[length];
        }

        public String nextString() {
            for (int idx = 0; idx < buf.length; ++idx)
                buf[idx] = symbols[random.nextInt(symbols.length)];
            return new String(buf);
        }
    }

    @Before
    public void setup() {
        new File(destinationFolder).mkdirs();
    }

    @Test
    public void primitivesFloatNumberTest() throws IOException, PdfException {
        String filename = "primitivesFloatNumberTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            PdfArray array = generatePdfArrayWithFloatNumbers(null, false);
            page.getPdfObject().put(TestArray, array);
            array.flush();
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesIntNumberTest() throws IOException, PdfException {
        String filename = "primitivesIntNumberTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            PdfArray array = generatePdfArrayWithIntNumbers(null, false);
            page.getPdfObject().put(TestArray, array);
            array.flush();
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesNameTest() throws IOException, PdfException {
        String filename = "primitivesNameTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            PdfArray array = generatePdfArrayWithNames(null, false);
            page.getPdfObject().put(TestArray, array);
            array.flush();
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesStringTest() throws IOException, PdfException {
        String filename = "primitivesStringTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            PdfArray array = generatePdfArrayWithStrings(null, false);
            page.getPdfObject().put(TestArray, array);
            array.flush();
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesBooleanTest() throws IOException, PdfException {
        String filename = "primitivesBooleanTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            page.getPdfObject().put(TestArray, generatePdfArrayWithBooleans(null, false));
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesFloatNumberIndirectTest() throws IOException, PdfException {
        String filename = "primitivesFloatNumberIndirectTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            page.getPdfObject().put(TestArray, generatePdfArrayWithFloatNumbers(pdfDoc, true));
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesIntNumberIndirectTest() throws IOException, PdfException {
        String filename = "primitivesIntNumberIndirectTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            page.getPdfObject().put(TestArray, generatePdfArrayWithIntNumbers(pdfDoc, true));
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesStringIndirectTest() throws IOException, PdfException {
        String filename = "primitivesStringIndirectTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            page.getPdfObject().put(TestArray, generatePdfArrayWithStrings(pdfDoc, true));
            page.flush();
        }
        pdfDoc.close();
    }



    @Test
    public void primitivesNameIndirectTest() throws IOException, PdfException {
        String filename = "primitivesNameIndirectTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            page.getPdfObject().put(TestArray, generatePdfArrayWithNames(pdfDoc, true));
            page.flush();
        }
        pdfDoc.close();
    }

    @Test
    public void primitivesBooleanIndirectTest() throws IOException, PdfException {
        String filename = "primitivesBooleanIndirectTest.pdf";
        FileOutputStream fos = new FileOutputStream(destinationFolder + filename);
        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        for (int i = 0; i < PageCount; i++) {
            PdfPage page = pdfDoc.addNewPage();
            page.getPdfObject().put(TestArray, generatePdfArrayWithBooleans(pdfDoc, true));
            page.flush();
        }
        pdfDoc.close();
    }


    @Test
    public void pdfNamesTest() {
        RandomString rnd = new RandomString(16);
        for (int i = 0; i < 10000000; i++) {
            new PdfName(rnd.nextString());
        }
    }


    private PdfArray generatePdfArrayWithFloatNumbers(PdfDocument doc, boolean indirects) {
        PdfArray array = new PdfArray().makeIndirect(doc);
        Random rnd = new Random();
        for (int i = 0; i < DefaultArraySize; i++) {
            PdfNumber num = new PdfNumber(rnd.nextFloat());
            if (indirects)
                num.makeIndirect(doc);
            array.add(num);
        }
        return array;
    }

    private PdfArray generatePdfArrayWithIntNumbers(PdfDocument doc, boolean indirects) {
        PdfArray array = new PdfArray().makeIndirect(doc);
        Random rnd = new Random();
        for (int i = 0; i < DefaultArraySize; i++) {
            array.add(new PdfNumber(rnd.nextInt()).makeIndirect(indirects ? doc : null));
        }
        return array;
    }

    private PdfArray generatePdfArrayWithStrings(PdfDocument doc, boolean indirects) {
        PdfArray array = new PdfArray().makeIndirect(doc);
        RandomString rnd = new RandomString(16);
        for (int i = 0; i < DefaultArraySize; i++) {
            array.add(new PdfString(rnd.nextString()).makeIndirect(indirects ? doc : null));
        }
        return array;
    }

    private PdfArray generatePdfArrayWithNames(PdfDocument doc, boolean indirects) {
        PdfArray array = new PdfArray().makeIndirect(doc);
        RandomString rnd = new RandomString(6);
        for (int i = 0; i < DefaultArraySize; i++) {
            array.add(new PdfName(rnd.nextString()).makeIndirect(indirects ? doc : null));
        }
        return array;
    }

    private PdfArray generatePdfArrayWithBooleans(PdfDocument doc, boolean indirects) {
        PdfArray array = new PdfArray().makeIndirect(doc);
        Random rnd = new Random();
        for (int i = 0; i < DefaultArraySize; i++) {
            array.add(new PdfBoolean(rnd.nextBoolean()).makeIndirect(indirects ? doc : null));
        }
        return array;
    }
}
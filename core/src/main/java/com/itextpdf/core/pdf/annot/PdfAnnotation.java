package com.itextpdf.core.pdf.annot;

import com.itextpdf.basics.PdfException;
import com.itextpdf.core.geom.Rectangle;
import com.itextpdf.core.pdf.*;
import com.itextpdf.core.pdf.action.PdfAction;
import com.itextpdf.core.pdf.layer.PdfOCG;

public class PdfAnnotation extends PdfObjectWrapper<PdfDictionary> {

    static public final int Invisible = 1;
    static public final int Hidden = 2;
    static public final int Print = 4;
    static public final int NoZoom = 8;
    static public final int NoRotate = 16;
    static public final int NoView = 32;
    static public final int ReadOnly = 64;
    static public final int Locked = 128;
    static public final int ToggleNoView = 256;
    static public final int LockedContents = 512;


    protected PdfPage page;

    public PdfAnnotation(PdfDocument document, Rectangle rect) throws PdfException {
        this(document, rect, null);
    }

    public PdfAnnotation(PdfDocument document, Rectangle rect, PdfPage page) throws PdfException {
        this(new PdfDictionary(), document);
        put(PdfName.Rect, new PdfArray(rect));
        if (page != null)
            put(PdfName.P, page);
        this.page = page;
    }

    public PdfAnnotation(PdfDictionary pdfObject, PdfDocument document) throws PdfException {
        this(pdfObject, document, null);
    }

    public PdfAnnotation(PdfDictionary pdfObject, PdfDocument document, PdfPage page) throws PdfException {
        super(pdfObject, document);
        this.page = page;
    }

    /**
     * Sets the layer this annotation belongs to.
     *
     * @param layer the layer this annotation belongs to
     */
    public void setLayer(final PdfOCG layer) throws PdfException {
        getPdfObject().put(PdfName.OC, layer.getIndirectReference());
    }

    public <T extends PdfAnnotation> T setAction(PdfAction action) {
        return put(PdfName.A, action);
    }

    public <T extends PdfAnnotation> T setAdditionalAction(PdfName key, PdfAction action) throws PdfException {
        PdfAction.setAdditionalAction(this, key, action);
        return (T) this;
    }

    public PdfString getContents() throws PdfException {
        return (PdfString) get(PdfName.Contents);
    }

    public <T extends PdfAnnotation> T setContents(PdfString contents) {
        return put(PdfName.Contents, contents);
    }

    public PdfDictionary getPageObject() throws PdfException {
        return (PdfDictionary) get(PdfName.P);
    }

    public PdfPage getPage() {
        return page;
    }

    public <T extends PdfAnnotation> T setPage(PdfPage page) {
        this.page = page;
        return put(PdfName.P, page);
    }

    public PdfString getName() throws PdfException {
        return (PdfString) get(PdfName.NM);
    }

    public <T extends PdfAnnotation> T setName(PdfString name) {
        return put(PdfName.NM, name);
    }

    public PdfString getDate() throws PdfException {
        return (PdfString) get(PdfName.M);
    }

    public <T extends PdfAnnotation> T setDate(PdfString date) {
        return put(PdfName.M, date);
    }

    public int getFlags() throws PdfException {
        PdfNumber f = (PdfNumber) get(PdfName.F);
        if (f != null)
            return f.getIntValue();
        else
            return 0;
    }

    public <T extends PdfAnnotation> T setFlags(int flags) {
        return put(PdfName.F, new PdfNumber(flags));
    }

    public <T extends PdfAnnotation> T setFlag(int flag) throws PdfException {
        int flags = getFlags();
        flags = flags | flag;
        return setFlags(flags);
    }

    public <T extends PdfAnnotation> T resetFlag(int flag) throws PdfException {
        int flags = getFlags();
        flags = flags & (~flag & 0xff);
        return setFlags(flags);
    }

    public boolean hasFlag(int flag) throws PdfException {
        int flags = getFlags();
        return (flags & flag) != 0;
    }

    public PdfDictionary getAppearanceDictionary() throws PdfException {
        return getPdfObject().getAsDictionary(PdfName.AP);
    }

    public PdfDictionary getAppearanceObject(PdfName appearanceType) throws PdfException {
        PdfDictionary ap = getAppearanceDictionary();
        if (ap != null) {
            return ap.getAsDictionary(appearanceType);
        }
        return null;
    }

    public PdfDictionary getNormalAppearanceObject() throws PdfException {
        return getAppearanceObject(PdfName.N);
    }

    public PdfDictionary getRolloverAppearanceObject() throws PdfException {
        return getAppearanceObject(PdfName.R);
    }

    public PdfDictionary getDownAppearanceObject() throws PdfException {
        return getAppearanceObject(PdfName.D);
    }

    public <T extends PdfAnnotation> T setAppearanceObject(PdfName appearanceType, PdfDictionary appearance) throws PdfException {
        PdfDictionary ap = getAppearanceDictionary();
        if (ap == null) {
            ap = new PdfDictionary();
            getPdfObject().put(PdfName.AP, ap);
        }
        ap.put(appearanceType, appearance);
        return (T) this;
    }

    public <T extends PdfAnnotation> T setNormalAppearanceObject(PdfDictionary appearance) throws PdfException {
        return setAppearanceObject(PdfName.N, appearance);
    }

    public <T extends PdfAnnotation> T setRolloverAppearanceObject(PdfDictionary appearance) throws PdfException {
        return setAppearanceObject(PdfName.R, appearance);
    }

    public <T extends PdfAnnotation> T setDownAppearanceObject(PdfDictionary appearance) throws PdfException {
        return setAppearanceObject(PdfName.D, appearance);
    }

    public <T extends PdfAnnotation> T setAppearance(PdfName appearanceType, PdfAnnotationAppearance appearance) throws PdfException {
        return setAppearanceObject(appearanceType, appearance.getPdfObject());
    }

    public <T extends PdfAnnotation> T setNormalAppearance(PdfAnnotationAppearance appearance) throws PdfException {
        return setAppearance(PdfName.N, appearance);
    }

    public <T extends PdfAnnotation> T setRolloverAppearance(PdfAnnotationAppearance appearance) throws PdfException {
        return setAppearance(PdfName.R, appearance);
    }

    public <T extends PdfAnnotation> T setDownAppearance(PdfAnnotationAppearance appearance) throws PdfException {
        return setAppearance(PdfName.D, appearance);
    }

    public PdfName getAppearanceState() throws PdfException {
        return (PdfName) get(PdfName.AS);
    }

    public <T extends PdfAnnotation> T setAppearanceState(PdfName as) {
        return put(PdfName.AS, as);
    }

    public PdfArray getBorder() throws PdfException {
        return (PdfArray) get(PdfName.Border);
    }

    public <T extends PdfAnnotation> T setBorder(PdfArray border) {
        return put(PdfName.Border, border);
    }

    public PdfArray getColor() throws PdfException {
        return (PdfArray) get(PdfName.C);
    }

    public <T extends PdfAnnotation> T setColor(PdfArray color) {
        return put(PdfName.C, color);
    }

    public int getStructParentIndex() throws PdfException {
        PdfNumber n = (PdfNumber) get(PdfName.StructParent);
        if (n == null)
            return -1;
        else
            return n.getIntValue();
    }

    public <T extends PdfAnnotation> T setStructParenyIndex(int structParentIndex) {
        return put(PdfName.StructParent, new PdfNumber(structParentIndex));
    }

    static public <T extends PdfAnnotation> T makeAnnotation(PdfObject pdfObject, PdfDocument document, PdfPage page) throws PdfException {
        if (pdfObject.isIndirectReference())
            pdfObject = ((PdfIndirectReference) pdfObject).getRefersTo();
        if (pdfObject.isDictionary()) {
            PdfDictionary dictionary = (PdfDictionary) pdfObject;
            PdfName subtype = dictionary.getAsName(PdfName.Subtype);
            if (PdfName.Link.equals(subtype))
                return (T) new PdfLinkAnnotation((PdfDictionary) pdfObject, document);
        }
        return null;
    }

    static public <T extends PdfAnnotation> T makeAnnotation(PdfObject pdfObject, PdfDocument document) throws PdfException {
        return makeAnnotation(pdfObject, document, null);
    }

}
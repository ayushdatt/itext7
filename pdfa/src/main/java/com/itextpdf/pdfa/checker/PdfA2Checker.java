package com.itextpdf.pdfa.checker;

import com.itextpdf.basics.geom.Rectangle;
import com.itextpdf.core.pdf.*;
import com.itextpdf.core.pdf.annot.PdfAnnotation;
import com.itextpdf.core.pdf.xobject.PdfImageXObject;
import com.itextpdf.pdfa.PdfAConformanceException;
import com.itextpdf.pdfa.PdfAConformanceLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class PdfA2Checker extends PdfA1Checker{

    protected static final HashSet<PdfName> forbiddenAnnotations = new HashSet<>(Arrays.asList(PdfName._3D, PdfName.Sound, PdfName.Screen, PdfName.Movie));
    protected static final HashSet<PdfName> forbiddenActions = new HashSet<>(Arrays.asList(PdfName.Launch, PdfName.Sound, PdfName.Movie,
            PdfName.ResetForm, PdfName.ImportData, PdfName.JavaScript, PdfName.Hide, PdfName.SetOCGState, PdfName.Rendition, PdfName.Trans, PdfName.GoTo3DView));
    static final int MAX_PAGE_SIZE = 14400;
    static final int MIN_PAGE_SIZE = 3;

    public PdfA2Checker(PdfAConformanceLevel conformanceLevel, String outputIntentColorSpace) {
        super(conformanceLevel, outputIntentColorSpace);
    }

    @Override
    protected double getMaxRealValue(){
        return Float.MAX_VALUE;
    }

    @Override
    protected int getMaxStringLength(){
        return  32767;
    }

    @Override
    public void checkInlineImage(PdfImageXObject inlineImage) {
        PdfObject filter = inlineImage.getPdfObject().get(PdfName.Filter);
        if (filter instanceof PdfName) {
            if (filter.equals(PdfName.LZWDecode))
                throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
            if (filter.equals(PdfName.Crypt)) {
                throw new PdfAConformanceException(PdfAConformanceException.CryptFilterIsNotPermitted);
            }
        } else if (filter instanceof PdfArray) {
            for (int i = 0; i < ((PdfArray) filter).size(); i++) {
                PdfName f = ((PdfArray) filter).getAsName(i);
                if (f.equals(PdfName.LZWDecode))
                    throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
                if (f.equals(PdfName.Crypt)) {
                    throw new PdfAConformanceException(PdfAConformanceException.CryptFilterIsNotPermitted);
                }
            }
        }
    }
    
    @Override
    protected void checkAnnotation(PdfDictionary annotDic) {
        PdfName subtype = annotDic.getAsName(PdfName.Subtype);

        if (subtype == null) {
            throw new PdfAConformanceException(PdfAConformanceException.AnnotationType1IsNotPermitted).setMessageParams("null");
        }
        if (forbiddenAnnotations.contains(subtype)) {
            throw new PdfAConformanceException(PdfAConformanceException.AnnotationType1IsNotPermitted).setMessageParams(subtype.getValue());
        }

        if (!subtype.equals(PdfName.Popup)) {
            PdfNumber f = annotDic.getAsNumber(PdfName.F);
            if (f == null) {
                throw new PdfAConformanceException(PdfAConformanceException.AnAnnotationDictionaryShallContainTheFKey);
            }
            int flags = f.getIntValue();
            if (!checkFlag(flags, PdfAnnotation.Print)
                    || checkFlag(flags, PdfAnnotation.Hidden)
                    || checkFlag(flags, PdfAnnotation.Invisible)
                    || checkFlag(flags, PdfAnnotation.NoView)
                    || checkFlag(flags, PdfAnnotation.ToggleNoView)) {
                throw new PdfAConformanceException(PdfAConformanceException.TheFKeysPrintFlagBitShallBeSetTo1AndItsHiddenInvisibleNoviewAndTogglenoviewFlagBitsShallBeSetTo0);
            }
            if (subtype.equals(PdfName.Text)) {
                if (!checkFlag(flags, PdfAnnotation.NoZoom) || !checkFlag(flags, PdfAnnotation.NoRotate)) {
                    throw new PdfAConformanceException(PdfAConformanceException.TextAnnotationsShouldSetTheNozoomAndNorotateFlagBitsOfTheFKeyTo1);
                }
            }
        }

        if (PdfName.Widget.equals(subtype) && (annotDic.containsKey(PdfName.AA) || annotDic.containsKey(PdfName.A))) {
            throw new PdfAConformanceException(PdfAConformanceException.WidgetAnnotationDictionaryOrFieldDictionaryShallNotIncludeAOrAAEntry);
        }

        if (checkStructure(conformanceLevel)) {
            if (contentAnnotations.contains(subtype) && !annotDic.containsKey(PdfName.Contents)) {
                throw new PdfAConformanceException(PdfAConformanceException.AnnotationOfType1ShouldHaveContentsKey).setMessageParams(subtype);
            }
        }

        PdfDictionary ap = annotDic.getAsDictionary(PdfName.AP);
        if (ap != null) {
            if (ap.containsKey(PdfName.R) || ap.containsKey(PdfName.D)) {
                throw new PdfAConformanceException(PdfAConformanceException.AppearanceDictionaryShallContainOnlyTheNKeyWithStreamValue);
            }
            PdfObject n = ap.get(PdfName.N);
            if (PdfName.Widget.equals(subtype) && PdfName.Btn.equals(annotDic.getAsName(PdfName.FT))) {
                if (n == null || !n.isDictionary())
                    throw new PdfAConformanceException(PdfAConformanceException.AppearanceDictionaryOfWidgetSubtypeAndBtnFieldTypeShallContainOnlyTheNKeyWithDictionaryValue);
            } else {
                if (n == null || !n.isStream())
                    throw new PdfAConformanceException(PdfAConformanceException.AppearanceDictionaryShallContainOnlyTheNKeyWithStreamValue);
            }
        } else {
            boolean isCorrectRect = false;
            PdfArray rect = annotDic.getAsArray(PdfName.Rect);
            if (rect != null && rect.size() == 4) {
                PdfNumber index0 = rect.getAsNumber(0);
                PdfNumber index1 = rect.getAsNumber(1);
                PdfNumber index2 = rect.getAsNumber(2);
                PdfNumber index3 = rect.getAsNumber(3);
                if (index0 != null && index1 != null && index2 != null && index3 != null &&
                        index0.getFloatValue() == index2.getFloatValue() && index1.getFloatValue() == index3.getFloatValue())
                    isCorrectRect = true;
            }
            if (!PdfName.Popup.equals(subtype) &&
                    !PdfName.Link.equals(subtype) &&
                    !isCorrectRect)
                throw new PdfAConformanceException(PdfAConformanceException.EveryAnnotationShallHaveAtLeastOneAppearanceDictionary);
        }
    }

    @Override
    protected void checkForm(PdfDictionary form) {
        if (form != null) {
            PdfBoolean needAppearances = form.getAsBoolean(PdfName.NeedAppearances);
            if (needAppearances != null && needAppearances.getValue()) {
                throw new PdfAConformanceException("needappearances.flag.of.the.interactive.form.dictionary.shall.either.not.be.present.or.shall.be.false");
            }
            if (checkStructure(conformanceLevel) && form.containsKey(PdfName.XFA)) {
                throw new PdfAConformanceException("the.interactive.form.dictionary.shall.not.contain.the.xfa.key");
            }
        }
    }

    @Override
    protected void checkCatalogValidEntries(PdfDictionary catalogDict) {
        if (catalogDict.containsKey(PdfName.NeedsRendering)) {
            throw new PdfAConformanceException("the.catalog.dictionary.shall.not.contain.the.needsrendering.key");
        }

        if (catalogDict.containsKey(PdfName.AA)) {
            throw new PdfAConformanceException(PdfAConformanceException.CatalogDictionaryShallNotContainAAEntry);
        }

        if (catalogDict.containsKey(PdfName.Requirements)) {
            throw new PdfAConformanceException(PdfAConformanceException.CatalogDictionaryShallNotContainRequirementsEntry);
        }

        PdfDictionary permissions = catalogDict.getAsDictionary(PdfName.Perms);
        if (permissions != null) {
            for (PdfName dictKey : permissions.keySet()) {
                if (PdfName.DocMDP.equals(dictKey)) {
                    PdfDictionary signatureDict = permissions.getAsDictionary(PdfName.DocMDP);
                    if (signatureDict != null) {
                        PdfArray references = signatureDict.getAsArray(PdfName.Reference);
                        if (references != null) {
                            for (int i = 0; i < references.size(); i++) {
                                PdfDictionary referenceDict = references.getAsDictionary(i);
                                if (referenceDict.containsKey(PdfName.DigestLocation)
                                        || referenceDict.containsKey(PdfName.DigestMethod)
                                        || referenceDict.containsKey(PdfName.DigestValue)) {
                                    throw new PdfAConformanceException(PdfAConformanceException.SigRefDicShallNotContDigestParam);
                                }
                            }
                        }
                    }
                } else if (PdfName.UR3.equals(dictKey)){}
                else {
                    throw new PdfAConformanceException(PdfAConformanceException.NoKeysOtherUr3andDocMdpShallBePresentInPerDict);
                }
            }
        }

        PdfDictionary namesDictionary = catalogDict.getAsDictionary(PdfName.Names);
        if (namesDictionary != null && namesDictionary.containsKey(PdfName.AlternatePresentations)) {
            throw new PdfAConformanceException(PdfAConformanceException.CatalogDictionaryShallNotContainAlternatepresentationsNamesEntry);
        }

        PdfDictionary oCProperties = catalogDict.getAsDictionary(PdfName.OCProperties);
        if (oCProperties != null) {
            ArrayList<PdfDictionary> configList = new ArrayList<>();
            PdfDictionary d = oCProperties.getAsDictionary(PdfName.D);
            if (d != null) {
                configList.add(d);
            }
            PdfArray configs = oCProperties.getAsArray(PdfName.Configs);
            if (configs != null) {
                for (PdfObject config : configs) {
                    configList.add((PdfDictionary) config);
                }
            }

            HashSet<PdfObject> ocgs = new HashSet<>();
            PdfArray ocgsArray = oCProperties.getAsArray(PdfName.OCGs);
            if (ocgsArray != null) {
                ocgs.addAll(ocgsArray);
            }

            HashSet<String> names = new HashSet<>();
            HashSet<PdfObject> order = new HashSet<>();
            for (PdfDictionary config : configList) {
                PdfString name = config.getAsString(PdfName.Name);
                if (name == null) {
                    throw new PdfAConformanceException(PdfAConformanceException.OptionalContentConfigurationDictionaryShallContainNameEntry);
                }
                if (!names.add(name.toUnicodeString())) {
                    throw new PdfAConformanceException(PdfAConformanceException.ValueOfNameEntryShallBeUniqueAmongAllOptionalContentConfigurationDictionaries);
                }
                if (config.containsKey(PdfName.AS)) {
                    throw new PdfAConformanceException(PdfAConformanceException.TheAsKeyShallNotAppearInAnyOptionalContentConfigurationDictionary);
                }
                PdfArray orderArray = config.getAsArray(PdfName.Order);
                if (orderArray != null) {
                    fillOrderRecursively(orderArray, order);
                }
            }

            if (order.size() != ocgs.size()) {
                throw new PdfAConformanceException(PdfAConformanceException.OrderArrayShallContainReferencesToAllOcgs);
            }
            order.retainAll(ocgs);
            if (order.size() != ocgs.size()) {
                throw new PdfAConformanceException(PdfAConformanceException.OrderArrayShallContainReferencesToAllOcgs);
            }
        }
    }

    @Override
    protected  void checkPageSize(PdfDictionary  page){
        PdfName[] boxNames = new PdfName[] {PdfName.MediaBox, PdfName.CropBox, PdfName.TrimBox, PdfName.ArtBox, PdfName.BleedBox};
        for (PdfName boxName: boxNames) {
            Rectangle box =  page.getAsRectangle(boxName);
            if (box !=null ) {
                float width = box.getWidth();
                float height = box.getHeight();
                if (width < MIN_PAGE_SIZE || width > MAX_PAGE_SIZE || height < MIN_PAGE_SIZE || height > MAX_PAGE_SIZE)
                    throw new PdfAConformanceException(PdfAConformanceException.PageLess3UnitsNoGreater14400InEitherDirection);
            }
        }
    }

    @Override
    protected void checkPdfStream(PdfStream stream) {

        if (stream.containsKey(PdfName.F) || stream.containsKey(PdfName.FFilter) || stream.containsKey(PdfName.FDecodeParams)) {
            throw new PdfAConformanceException(PdfAConformanceException.StreamObjDictShallNotContainForFFilterOrFDecodeParams);
        }

        PdfObject filter = stream.get(PdfName.Filter);
        if (filter instanceof PdfName) {
            if (filter.equals(PdfName.LZWDecode))
                throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
            if (filter.equals(PdfName.Crypt)) {
                PdfDictionary decodeParams = stream.getAsDictionary(PdfName.DecodeParms);
                if (decodeParams != null) {
                    PdfString cryptFilterName = decodeParams.getAsString(PdfName.Name);
                    if (cryptFilterName != null && !cryptFilterName.equals(PdfName.Identity)) {
                        throw new PdfAConformanceException(PdfAConformanceException.NotIdentityCryptFilterIsNotPermitted);
                    }
                }
            }
        } else if (filter instanceof PdfArray) {
            for (int i = 0; i < ((PdfArray) filter).size(); i++) {
                PdfName f = ((PdfArray) filter).getAsName(i);
                if (f.equals(PdfName.LZWDecode))
                    throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
                if (f.equals(PdfName.Crypt)) {
                    PdfArray decodeParams = stream.getAsArray(PdfName.DecodeParms);
                    if (decodeParams != null && i < decodeParams.size()) {
                        PdfDictionary decodeParam = decodeParams.getAsDictionary(i);
                        PdfString cryptFilterName = decodeParam.getAsString(PdfName.Name);
                        if (cryptFilterName != null && !cryptFilterName.equals(PdfName.Identity)) {
                            throw new PdfAConformanceException(PdfAConformanceException.NotIdentityCryptFilterIsNotPermitted);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void checkAction(PdfDictionary action) {
        PdfName s = action.getAsName(PdfName.S);
        if (forbiddenActions.contains(s)) {
            throw new PdfAConformanceException(PdfAConformanceException._1ActionsIsNotAllowed).setMessageParams(s.getValue());
        }
        if (s.equals(PdfName.Named)) {
            PdfName n = action.getAsName(PdfName.N);
            if (n != null && !allowedNamedActions.contains(n)) {
                throw new PdfAConformanceException(PdfAConformanceException.NamedActionType1IsNotAllowed).setMessageParams(n.getValue());
            }
        }
        if (s.equals(PdfName.SetState) || s.equals(PdfName.NoOp)) {
            throw new PdfAConformanceException(PdfAConformanceException.DeprecatedSetStateAndNoOpActionsAreNotAllowed);
        }
    }

    @Override
    protected void checkPage(PdfDictionary pageDict) {
        if (pageDict.containsKey(PdfName.AA)) {
            throw new PdfAConformanceException(PdfAConformanceException.PageDictionaryShallNotContainAAEntry);
        }

        if (pageDict.containsKey(PdfName.PresSteps)) {
            throw new PdfAConformanceException(PdfAConformanceException.PageDictionaryShallNotContainPressstepsEntry);
        }
    }

    @Override
    protected HashSet<PdfName> getForbiddenActions() {
        return forbiddenActions;
    }

    @Override
    protected HashSet<PdfName> getAllowedNamedActions() {
        return allowedNamedActions;
    }

    private void fillOrderRecursively(PdfArray orderArray, HashSet<PdfObject> order) {
        for (PdfObject orderItem : orderArray) {
            if (!orderItem.isArray()) {
                order.add(orderItem);
            } else {
                fillOrderRecursively((PdfArray) orderItem, order);
            }
        }
    }
}
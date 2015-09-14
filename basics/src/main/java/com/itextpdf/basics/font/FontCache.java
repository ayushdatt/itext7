package com.itextpdf.basics.font;

import com.itextpdf.basics.IntHashtable;
import com.itextpdf.basics.PdfException;
import com.itextpdf.basics.Utilities;
import com.itextpdf.basics.font.cmap.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class FontCache {

    /**
     * The path to the font resources.
     */
    public static final String CMAP_RESOURCE_PATH = FontConstants.RESOURCE_PATH + "cmap/";

    private static final Map<String, Map<String, Object>> allFonts = new HashMap<String, Map<String, Object>>();
    private static final Map<String, Set<String>> registryNames = new HashMap<String, Set<String>>();

    private static final String CJK_REGISTRY_FILENAME = "cjk_registry.properties";
    private static final String FONTS_PROP = "fonts";
    private static final String REGISTRY_PROP = "Registry";
    private static final String W_PROP = "W";
    private static final String W2_PROP = "W2";

    private static ConcurrentHashMap<String, FontProgram> fontCache = new ConcurrentHashMap<String, FontProgram>();

    static {
        try {
            loadRegistry();

            for (String font : registryNames.get(FONTS_PROP)) {
                allFonts.put(font, readFontProperties(font));
            }
        } catch (Exception ignored) {
            // TODO: add logger (?)
        }
    }

    /**
     * Checks if the font with the given name and encoding is one
     * of the predefined CID fonts.
     * @param fontName the font name.
     * @param cmap the encoding.
     * @return {@code true} if it is CJKFont.
     */
    protected static boolean isCidFont(String fontName, String cmap) {
        if (!registryNames.containsKey(FONTS_PROP)) {
            return false;
        } else if (!registryNames.get(FONTS_PROP).contains(fontName)) {
            return false;
        } else if (cmap.equals(PdfEncodings.IDENTITY_H) || cmap.equals(PdfEncodings.IDENTITY_V)) {
            return true;
        }

        String registry = (String) allFonts.get(fontName).get(REGISTRY_PROP);
        Set<String> cmaps = registryNames.get(registry);

        return cmaps != null && cmaps.contains(cmap);
    }

    public static String getCompatibleCidFont(String cmap) {
        for (Map.Entry<String, Set<String>> e : registryNames.entrySet()) {
            if (e.getValue().contains(cmap)) {
                String registry = e.getKey();
                for (Map.Entry<String, Map<String, Object>> e1 : allFonts.entrySet()) {
                    if (registry.equals(e1.getValue().get(REGISTRY_PROP)))
                        return e1.getKey();
                }
            }
        }
        return null;
    }

    public static Map<String, Map<String, Object>> getAllFonts() {
        return allFonts;
    }

    public static Map<String, Set<String>> getRegistryNames() {
        return registryNames;
    }

    public static CMapCidUni getCid2UniCmap(String uniMap) {
        CMapCidUni cidUni = new CMapCidUni();
        return parseCmap(uniMap, cidUni);
    }

    public static CMapUniCid getUni2CidCmap(String uniMap) {
        CMapUniCid uniCid = new CMapUniCid();
        return parseCmap(uniMap, uniCid);
    }

    public static CMapByteCid getByte2CidCmap(String cmap) {
        CMapByteCid uniCid = new CMapByteCid();
        return parseCmap(cmap, uniCid);
    }

    public static CMapCidByte getCid2Byte(String cmap) {
        CMapCidByte cidByte = new CMapCidByte();
        return parseCmap(cmap, cidByte);
    }

    public static FontProgram getFont(String fontName, String encoding) {
        String key = getFontCacheKey(fontName, encoding);
        return fontCache.get(key);
    }

    public static void saveFont(FontProgram font, String fontName, String encoding) {
        // for most of the fonts we can retrieve encoding from FontProgram, but
        // for Cid there is no such possibility, since it is used in conjunction
        // with cmap to produce Type0 font, so I added fontName and encoding parameters
        // just for convenience.
        // TODO: probably it's better to declare saveFont(FontProgram) and saveFont(CidFont, encoding or cmap) in the future
        String key = getFontCacheKey(fontName, encoding);
        fontCache.put(key, font);
    }

    private static void loadRegistry() throws IOException {
        InputStream is = Utilities.getResourceStream(CMAP_RESOURCE_PATH + CJK_REGISTRY_FILENAME);

        try {
            Properties p = new Properties();
            p.load(is);

            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                String value = (String) entry.getValue();
                String[] splitValue = value.split(" ");
                Set<String> set = new HashSet<String>();

                for (String s : splitValue) {
                    if (!s.isEmpty()) {
                        set.add(s);
                    }
                }

                registryNames.put((String) entry.getKey(), set);
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private static Map<String, Object> readFontProperties(String name) throws IOException {
        InputStream is = Utilities.getResourceStream(CMAP_RESOURCE_PATH + name + ".properties");

        try {
            Properties p = new Properties();
            p.load(is);

            Map<String, Object> fontProperties = new HashMap<String, Object>((Map) p);
            fontProperties.put(W_PROP, createMetric((String) fontProperties.get(W_PROP)));
            fontProperties.put(W2_PROP, createMetric((String) fontProperties.get(W2_PROP)));

            return fontProperties;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private static IntHashtable createMetric(String s) {
        IntHashtable h = new IntHashtable();
        StringTokenizer tk = new StringTokenizer(s);

        while (tk.hasMoreTokens()) {
            int n1 = Integer.parseInt(tk.nextToken());
            h.put(n1, Integer.parseInt(tk.nextToken()));
        }

        return h;
    }

    private static <T extends AbstractCMap> T parseCmap(String name, T cmap) {
        try {
            CMapParser.parseCid(name, cmap, new CMapLocationResource());
        } catch (IOException e) {
            throw new PdfException(PdfException.IoException, e);
        }
        return cmap;
    }

    private static String getFontCacheKey(String fontName, String encoding) {
        return fontName + "\n" + encoding;
    }
}
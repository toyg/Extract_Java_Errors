/*
 * Copyright (c) Giacomo Lacava, 2019.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */

package com.autoepm.utils.hfm;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * This is a simple utility to extract all error messages from a (EPM) bundle
 * <p>
 * CLI parameters: directory_with_bundle_files name_of_bundle output_file_path
 */

public class Main {

    public static void main(String[] args) throws MalformedURLException {

        // collect arguments
        String inDir = args[0];
        String bundleName = args[1];
        String outFile = args[2];
        File basePathFile = Paths.get(inDir).toFile();
        if (!basePathFile.exists()) quitWithError("Invalid directory specified: " + basePathFile.getAbsolutePath());

        File outFilePath = Paths.get(outFile).toFile();
        String bundlePrefix = bundleName + "_";
        // prepare regex and filter
        Pattern regex = Pattern.compile(bundlePrefix + "([A-z]+_[A-z]+)\\.properties");
        FilenameFilter nameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.startsWith(bundlePrefix);
            }
        };

        // set up custom classloader targeting the bundle directory,
        // so that the bundle can be found later
        URL[] urls = {basePathFile.toURI().toURL()};
        ClassLoader loader = new URLClassLoader(urls);
        try {
            // get bundle keys from a default locale (en-us)
            ResourceBundle bundle = ResourceBundle.getBundle(bundleName, Locale.forLanguageTag("en_us"), loader);
            List<String> keys = Collections.list(bundle.getKeys());
            // find all locales available and build a bundle for each
            String[] names = basePathFile.list(nameFilter);
            if (names == null) quitWithError("No locales available at " + basePathFile.getAbsolutePath());
            List<ResourceBundle> bundles = Arrays.stream(names)
                    .map(name -> regex.matcher(name).replaceFirst("$1"))
                    .map(code -> {
                        String correctCode = code.toLowerCase().equals("ar_ar") ? "ar_SA" : code; // EPM coders got this locale wrong ("Arabic Argentina"...)
                        return Locale.forLanguageTag(correctCode.replace("_", "-"));
                    })
                    // UTF8Control ensures UTF-8 properties files are loaded correctly
                    .map(locale -> ResourceBundle.getBundle(bundleName, locale, loader, new UTF8Control()))
                    .collect(Collectors.toList());
            /*
             for(ResourceBundle localizedBundle : bundles) {
             System.out.println(localizedBundle.getLocale().getCountry());
             }
             */
            // write it all out
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFilePath), Charset.forName("UTF-8"))) {
                for (String key : keys) {
                    StringBuilder sb = new StringBuilder();
                    for (ResourceBundle localizedBundle : bundles) {
                        // if you want to deal with RTL...
                        //boolean rightToLeftLanguage = isRightToLeft(key,localizedBundle);
                        sb.append(localizedBundle.getString(key));
                        sb.append("\n");
                    }
                    writer.write(sb.toString() + "----\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MissingResourceException mre) {
            quitWithError("Specified bundle not found.");
        }
    }


    static boolean isRightToLeft(String key, ResourceBundle myBundle) {
        List<String> rtlLanguages = Collections.unmodifiableList(Arrays.asList("ar"));
        if (rtlLanguages.contains(myBundle.getLocale().getLanguage().toLowerCase())) {
            // here one should do something a bit more clever really, this only works for arabic
            for (char charac : myBundle.getString(key).toCharArray()) {
                if (Character.UnicodeBlock.of(charac).equals(Character.UnicodeBlock.ARABIC)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void quitWithError(String message) {
        System.out.println(message);
        System.exit(1);
    }

}

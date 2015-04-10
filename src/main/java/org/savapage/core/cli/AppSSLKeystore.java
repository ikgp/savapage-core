/*
 * This file is part of the SavaPage project <http://savapage.org>.
 * Copyright (c) 2011-2014 Datraverse B.V.
 * Author: Rijk Ravestein.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please contact Datraverse B.V. at this
 * address: info@datraverse.com
 */
package org.savapage.core.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.savapage.core.community.CommunityDictEnum;
import org.savapage.core.config.ConfigManager;
import org.savapage.core.config.RunMode;
import org.savapage.core.jpa.tools.DatabaseTypeEnum;

/**
 *
 * @author Datraverse B.V.
 *
 */
public final class AppSSLKeystore extends AbstractApp {

    /*
     * !!! IMPORTANT !!!
     *
     * Adds the Bouncy castle provider to java security.
     *
     * This is needed so you can use the "BC" provider in security methods.
     */
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /** */
    private static final String CLI_SWITCH_DEFAULT = "d";
    /** */
    private static final String CLI_SWITCH_DEFAULT_LONG = "default";

    /** */
    private static final String CLI_OPTION_CREATE = "create";
    /** */
    private static final String CLI_SWITCH_FORCE = "f";
    /** */
    private static final String CLI_SWITCH_FORCE_LONG = "force";
    /** */
    private static final String CLI_OPTION_SYSTEM_NAME = "system-name";

    /** */
    private final String myKeystoreDefault;

    /** */
    private final String myHostnameDefault;

    /** */
    private String myKeystorePassword;

    /** */
    private String myKeyEntryPassword;

    /**
     * @throws Exception
     *
     */
    private AppSSLKeystore() throws Exception {

        final String relPath = "data/default-ssl-keystore";

        if (ConfigManager.getServerHome() == null) {
            myKeystoreDefault = relPath;
        } else {
            myKeystoreDefault = ConfigManager.getServerHome() + "/" + relPath;
        }
        myHostnameDefault = InetAddress.getLocalHost().getHostName();

    }

    /**
     * @throws IOException
     *
     */
    synchronized private void lazyCreateDefaultPasswords() throws IOException {

        final int pwLength = 48;
        final String propKeyPassword = "password";

        Properties props = new Properties();
        File filePw =
                new File(ConfigManager.getServerHome()
                        + "/data/default-ssl-keystore.pw");

        InputStream istr = null;
        Writer writer = null;
        String pw = null;

        try {

            if (!filePw.exists()) {
                writer = new FileWriter(filePw);
                pw = RandomStringUtils.randomAlphanumeric(pwLength);
                props.setProperty(propKeyPassword, pw);
                props.store(writer, "Keep the contents of this "
                        + "file at a secure place.");
            } else {
                istr = new java.io.FileInputStream(filePw);
                props.load(istr);
                pw = props.getProperty(propKeyPassword);
            }

        } finally {
            if (writer != null) {
                writer.close();
            }
            if (istr != null) {
                istr.close();
            }
        }

        myKeystorePassword = pw;
        myKeyEntryPassword = pw;
    }

    /**
     * Creates a keystore with a self-signed SSL certificate.
     *
     * http://www.bouncycastle.org/wiki/display/JA1/BC+Version+2+APIs
     *
     * http://stackoverflow.com/questions/4828818/problem-obtaining-public-key-
     * from-subjectpublickeyinfo
     *
     * @param keystoreFile
     *            The keystore file.
     * @param holderCommonName
     *            CN name of the holder.
     * @param keystorePassword
     *            Password for the keystore.
     * @param keyPassword
     *            Password for the SSL certificate key entry in the keystore.
     *
     * @throws Exception
     *             When things went wrong.
     */
    private void createKeystore(final File keystoreFile,
            final String holderCommonName, final String keystorePassword,
            final String keyPassword) throws Exception {

        /*
         * GENERATE THE PUBLIC/PRIVATE RSA KEY PAIR
         */
        final int keysize = 2048; // 1024, 2048, 4096

        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA", "BC");

        keyPairGenerator.initialize(keysize, new SecureRandom());
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();

        /*
         * Yesterday, as notBefore date
         */
        final Date dateNotBefore =
                new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
        /*
         * 10 Years After, as notAfter date
         */
        final Date dateNotAfter =
                new Date(System.currentTimeMillis()
                        + (10L * 365 * 24L * 60L * 60L * 1000L));

        /*
         * GENERATE THE X509 CERTIFICATE
         */
        byte[] publickeyb = keyPair.getPublic().getEncoded();

        SubjectPublicKeyInfo subPubKeyInfo =
                new SubjectPublicKeyInfo(
                        (ASN1Sequence) ASN1Primitive.fromByteArray(publickeyb));

        final X500Name holder = new X500Name("CN=" + holderCommonName);
        final X500Name issuer =
                new X500Name("CN=" + CommunityDictEnum.SAVAPAGE.getWord()
                        + " Self-Signed Certificate");
        final BigInteger serial = BigInteger.ONE;

        /*
         * SAMPLE:
         *
         * "CN=SavaPage Self-Signed Certificate, OU=SavaPage, O=Datraverse B.V.,
         * L=Almere, ST=Flevoland, C=NL";
         */

        /*
         * SAMPLE:
         *
         * "CN=Apache Wicket Quickstart Certificate, OU=Apache Wicket, " +
         * "O=The Apache Software Foundation, L=Unknown, ST=Unknown, C=Unknown";
         */

        // X509v1CertificateBuilder v1CertGen = new X509v1CertificateBuilder(

        /*
         * VeriSign uses the concept of classes for different types of digital
         * certificates
         *
         * Class 1 for individuals, intended for email.
         *
         * Class 2 for organizations, for which proof of identity is required.
         *
         * Class 3 for servers and software signing, for which independent
         * verification and checking of identity and authority is done by the
         * issuing certificate authority.
         */
        X509v3CertificateBuilder certBuilder =
                new X509v3CertificateBuilder(issuer, serial, dateNotBefore,
                        dateNotAfter, holder, subPubKeyInfo);

        ContentSigner contentSigner =
                new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC")
                        .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(contentSigner);

        X509Certificate cert =
                new JcaX509CertificateConverter().setProvider("BC")
                        .getCertificate(certHolder);

        /*
         * Create empty keystore
         */
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null);

        /*
         * Fill keystore
         */
        final String alias = CommunityDictEnum.SAVAPAGE.getWord();

        ks.setKeyEntry(alias, keyPair.getPrivate(), keyPassword.toCharArray(),
                new X509Certificate[] { cert });

        /*
         * Write keystore
         */
        FileOutputStream ostr = new FileOutputStream(keystoreFile);
        ks.store(ostr, keystorePassword.toCharArray());
        ostr.close();
    }

    /**
     *
     * @param ksFile
     *            The keystore file.
     * @param password
     * @throws Exception
     */
    @SuppressWarnings("unused")
    private void displayKeystore(final File ksFile, final String password)
            throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(ksFile), password.toCharArray());
        final String alias = ks.aliases().nextElement();
        X509Certificate t = (X509Certificate) ks.getCertificate(alias);

        getDisplayStream().println("Version    : " + t.getVersion());
        getDisplayStream().println(
                "Serial#    : " + t.getSerialNumber().toString(16));
        getDisplayStream().println("SubjectDN  : " + t.getSubjectDN());
        getDisplayStream().println("IssuerDN   : " + t.getIssuerDN());
        getDisplayStream().println("NotBefore  : " + t.getNotBefore());
        getDisplayStream().println("NotAfter   : " + t.getNotAfter());
        getDisplayStream().println("SigAlgName : " + t.getSigAlgName());

        // byte[] sig = t.getSignature();
        // getDisplayStream().println(new BigInteger(sig).toString(16));
        // PublicKey pk = t.getPublicKey();
        // byte[] pkenc = pk.getEncoded();
        // for (int i = 0; i < pkenc.length; i++) {
        // getDisplayStream().print(pkenc[i] + ",");
        // }
    }

    @Override
    protected final Options createCliOptions() throws Exception {

        Options options = new Options();

        //
        options.addOption(CLI_SWITCH_HELP, CLI_SWITCH_HELP_LONG, false,
                "Displays this help text.");

        //
        options.addOption(CLI_SWITCH_DEFAULT, CLI_SWITCH_DEFAULT_LONG, false,
                "Creates the default keystore file '"
                        + new File(myKeystoreDefault).getAbsolutePath() + "'.");

        //
        OptionBuilder.hasArg(true);
        OptionBuilder.withArgName("FILE");
        OptionBuilder.withLongOpt(CLI_OPTION_CREATE);
        OptionBuilder.withDescription("Creates a specific keystore file.");
        options.addOption(OptionBuilder.create());

        //
        OptionBuilder.hasArg(true);
        OptionBuilder.withArgName("NAME");
        OptionBuilder.withLongOpt(CLI_OPTION_SYSTEM_NAME);
        OptionBuilder
                .withDescription("The name of the computer/server used for the "
                        + "SSL Certificate. If not set the current "
                        + "computer name '" + myHostnameDefault + "' is used.");
        options.addOption(OptionBuilder.create());

        //
        options.addOption(CLI_SWITCH_FORCE, CLI_SWITCH_FORCE_LONG, false,
                "Force. Overwrite any existing keystore file.");

        //
        return options;
    }

    @Override
    protected int run(final String[] args) throws Exception {

        final String cmdLineSyntax = "[OPTION]...";

        // ......................................................
        // Parse parameters from CLI
        // ......................................................
        Options options = createCliOptions();
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            getDisplayStream().println(e.getMessage());
            usage(cmdLineSyntax, options);
            return EXIT_CODE_PARMS_PARSE_ERROR;
        }

        // ......................................................
        // Help needed?
        // ......................................................
        if (args.length == 0 || cmd.hasOption(CLI_SWITCH_HELP)
                || cmd.hasOption(CLI_SWITCH_HELP_LONG)) {
            usage(cmdLineSyntax, options);
            return EXIT_CODE_OK;
        }

        // ......................................................
        //
        // ......................................................
        lazyCreateDefaultPasswords();

        // ......................................................
        //
        // ......................................................
        final boolean forceCreate =
                (cmd.hasOption(CLI_SWITCH_FORCE) || cmd
                        .hasOption(CLI_SWITCH_FORCE_LONG));

        /*
         * Create the default key store
         */
        if (cmd.hasOption(CLI_SWITCH_DEFAULT)
                || cmd.hasOption(CLI_SWITCH_DEFAULT_LONG)) {

            File file = new File(myKeystoreDefault);
            final boolean exists = file.exists();

            if (forceCreate || !exists) {
                createKeystore(file, myHostnameDefault, myKeystorePassword,
                        myKeyEntryPassword);
            }

            if (!forceCreate && exists) {
                getDisplayStream().println(
                        "SSL key store not created. " + "File already exist.");
            }

            return EXIT_CODE_OK;
        }

        /*
         * Create the custom key store
         */
        final String systemName =
                cmd.getOptionValue(CLI_OPTION_SYSTEM_NAME, myHostnameDefault);

        final File keystore =
                new File(cmd.getOptionValue(CLI_OPTION_CREATE,
                        myKeystoreDefault));

        if (keystore.isDirectory()) {
            getErrorDisplayStream().println(
                    "Error: keystore " + keystore.getAbsolutePath()
                            + " refers to a directory");
            return EXIT_CODE_ERROR;
        }

        if (keystore.exists() && !forceCreate) {
            getErrorDisplayStream().println(
                    "Error: SSL key store " + keystore.getAbsolutePath()
                            + " already exist. "
                            + "Use the force option to overwrite");
            return EXIT_CODE_ERROR;
        }

        createKeystore(keystore, systemName, myKeystorePassword,
                myKeyEntryPassword);

        return EXIT_CODE_OK;
    }

    /**
     * Initialize as basic library. See
     * {@link ConfigManager#initAsBasicLibrary(Properties)}.
     */
    @Override
    protected void onInit() throws Exception {
        ConfigManager.instance().init(RunMode.LIB, DatabaseTypeEnum.Internal);
    }

    /**
     * IMPORTANT: MUST return void, use System.exit() to get an exit code on JVM
     * execution.
     *
     * @param args
     *            The command-line arguments.
     */
    public static void main(final String[] args) {
        int status = EXIT_CODE_EXCEPTION;
        try {
            AppSSLKeystore app = new AppSSLKeystore();
            status = app.run(args);
        } catch (Exception e) {
            getErrorDisplayStream().println(e.getMessage());
        }
        System.exit(status);
    }

}

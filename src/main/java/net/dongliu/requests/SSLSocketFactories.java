package net.dongliu.requests;

import net.dongliu.commons.concurrent.Lazy;
import net.dongliu.commons.concurrent.WeakLoader;
import net.dongliu.commons.exception.Exceptions;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * Utils method for ssl socket factory
 *
 * @author Liu Dong
 */
class SSLSocketFactories {

    // To reuse the connection, settings on the underlying socket must use the exact same objects.

    private static Lazy<SSLSocketFactory> sslSocketFactoryLazy = Lazy.create(() -> {
        TrustManager trustManager = new TrustAllTrustManager();
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw Exceptions.sneakyThrow(e);
        }

        return sslContext.getSocketFactory();
    });

    public static SSLSocketFactory getTrustAllSSLSocketFactory() {
        return sslSocketFactoryLazy.get();
    }

    private static final WeakLoader<Collection<CertificateInfo>, SSLSocketFactory> loader = WeakLoader.create(
            certs -> {
                TrustManager trustManager = new CustomCertTrustManager(certs);
                SSLContext sslContext;
                try {
                    sslContext = SSLContext.getInstance("SSL");
                    sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    throw Exceptions.sneakyThrow(e);
                }

                return sslContext.getSocketFactory();
            }
    );

    public static SSLSocketFactory getCustomSSLSocketFactory(Collection<CertificateInfo> certs) {
        return loader.get(certs);
    }

    static class TrustAllTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates,
                                       String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates,
                                       String s) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

    static class CustomCertTrustManager implements X509TrustManager {

        private final Collection<CertificateInfo> certs;
        private final X509TrustManager sunJSSEX509TrustManager;

        public CustomCertTrustManager(Collection<CertificateInfo> certs) {
            this.certs = certs;
            this.sunJSSEX509TrustManager = load();
        }

        private X509TrustManager load() {
            KeyStore ks;
            try {
                ks = KeyStore.getInstance("JKS");
            } catch (KeyStoreException e) {
                throw Exceptions.sneakyThrow(e);
            }
            for (CertificateInfo cert : certs) {
                try {
                    ks.load(new FileInputStream(cert.getPath()), cert.getPassword() == null ?
                            null : cert.getPassword().toCharArray());
                } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
                    throw Exceptions.sneakyThrow(e);
                }
            }
            TrustManagerFactory trustManagerFactory;
            try {
                trustManagerFactory = TrustManagerFactory.getInstance("SunX509", "SunJSSE");
                trustManagerFactory.init(ks);
            } catch (NoSuchAlgorithmException | NoSuchProviderException | KeyStoreException e) {
                throw Exceptions.sneakyThrow(e);
            }

            for (TrustManager trustManger : trustManagerFactory.getTrustManagers()) {
                if (trustManger instanceof X509TrustManager) {
                    return (X509TrustManager) trustManger;
                }
            }
            throw new RuntimeException("Couldn't initialize X509TrustManager");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            sunJSSEX509TrustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            sunJSSEX509TrustManager.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return sunJSSEX509TrustManager.getAcceptedIssuers();
        }
    }

}
module com.anonet.anonetclient {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.base;
    requires java.net.http;

    exports com.anonet.anonetclient;
    exports com.anonet.anonetclient.crypto;
    exports com.anonet.anonetclient.crypto.session;
    exports com.anonet.anonetclient.identity;
    exports com.anonet.anonetclient.lan;
    exports com.anonet.anonetclient.transfer;
    exports com.anonet.anonetclient.discovery;
    exports com.anonet.anonetclient.publicnet;
    exports com.anonet.anonetclient.dht;
    exports com.anonet.anonetclient.relay;
    exports com.anonet.anonetclient.ui;

    opens com.anonet.anonetclient.ui to javafx.graphics;
}

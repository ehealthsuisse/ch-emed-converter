package ch.emed.converter.rest.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import ch.emed.converter.rest.AbstractRestTest;
import ch.emed.converter.rest.RestTestSupport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T039 (FR-015, constitution IV): conversions make no outbound connection. Every URL-based client
 * of the JVM (URLConnection, java.net.http, OkHttp used by HAPI) asks the default {@link
 * ProxySelector}; it records the target and routes it to a closed local port, so any attempt is
 * both visible and blocked. Raw sockets bypass the selector and are not covered.
 */
class NoOutboundNetworkTest extends AbstractRestTest {

  private final List<URI> attempts = new CopyOnWriteArrayList<>();
  private ProxySelector original;

  @BeforeEach
  void blockOutbound() {
    original = ProxySelector.getDefault();
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            attempts.add(uri);
            return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 9)));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress address, IOException e) {}
        });
  }

  @AfterEach
  void restore() {
    ProxySelector.setDefault(original);
  }

  @Test
  void corpusMakesNoOutboundConnection() throws Exception {
    for (var input : RestTestSupport.corpus(RestTestSupport.CHMED16A)) {
      mvc.perform(
          convert(
              "chmed16a", "ch-emed", RestTestSupport.read(input), RestTestSupport.withAuthor("")));
    }
    for (var input : RestTestSupport.corpus(RestTestSupport.CH_EMED)) {
      mvc.perform(convert("ch-emed", "chmed16a", RestTestSupport.read(input), null));
    }

    assertThat(attempts).as("outbound connection attempts").isEmpty();
  }
}

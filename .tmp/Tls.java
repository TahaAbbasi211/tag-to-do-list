public class Tls { public static void main(String[] a) throws Exception {
  for (String u : a) { try { java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
    c.setRequestMethod("HEAD"); c.setConnectTimeout(20000); c.setReadTimeout(20000);
    System.out.println("OK " + c.getResponseCode() + " " + u); } catch (Exception e) { System.out.println("FAIL " + u + " :: " + e); } } } }

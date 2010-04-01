package net.sf.onioncoffee.test;



public class LocationTest {

//    @Test
//    public void testLocation() throws UnknownHostException, IOException, SAXException, ParserConfigurationException, XPathExpressionException {
//        NamespaceContextHelper ctx = new NamespaceContextHelper();
//        ctx.addMapping("gml", "http://www.opengis.net/gml");
//        ctx.addMapping("hip", "http://www.hostip.info/api");
//        
//        String httpResponse = HttpUtil.getHttp("http://checkip.dyndns.org/");
//        Pattern p = Pattern.compile("\\s(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
//        Matcher m = p.matcher(httpResponse);
//        if (m.find()) {
//          String ip = m.group(1);
//          HttpClient client = new HttpClient();
//          GetMethod method = new GetMethod("http://api.hostip.info/?ip=" + ip);
//          int result = client.executeMethod(method);
//          assertTrue(result == 200);
//          String response = StringUtil.read(method.getResponseBodyAsStream());
//          HostIp hostIp = HostIp.parseXmlString(response);
//          System.out.println(hostIp);
//        }
//
//    
//    }
}

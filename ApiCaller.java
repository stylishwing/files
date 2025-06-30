package Function;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import baselinesdiff.DifferenceBaseline;

public class ApiCaller {

	public static String user_DC;
	public static String password_DC;
	public static String user_RES;
	public static String password_RES;
	public static String dcIP;
	public static String resUATIP;
	public static String resPRODIP;
	public static String[] ipProd = new String[2];
	public static String filePath;
	public static Logger logger = Logger.getLogger(ApiCaller.class);

	public static void init() {
		Properties prop = new Properties();
		InputStream in;
		try {
			in = new BufferedInputStream(new FileInputStream(
					new File(DifferenceBaseline.class.getResource("/properties/odmAPI.properties").getFile())));
			prop.load(in);
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}

		user_DC = prop.getProperty("odmDC.user");
		password_DC = prop.getProperty("odmDC.password");
		user_RES = prop.getProperty("odmRES.user");
		password_RES = prop.getProperty("odmRES.password");
		dcIP = prop.getProperty("odmDC.ip");
		resUATIP = prop.getProperty("resUAT.ip");
		resPRODIP = prop.getProperty("resProd.ip");

		ipProd[0] = prop.getProperty("resProd.ip");
		ipProd[1] = prop.getProperty("resProd.ip2");

		filePath = prop.getProperty("out.filePath");

	}

	public static CloseableHttpClient getClient(String user, String password)
			throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
		SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
				.register("https", sslsf).register("http", new PlainConnectionSocketFactory()).build();
		BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(
				socketFactoryRegistry);
		CredentialsProvider provider = new BasicCredentialsProvider();
		UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
		provider.setCredentials(AuthScope.ANY, credentials);
		CloseableHttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider)
				.setSSLSocketFactory(sslsf).setConnectionManager(connectionManager).build();
		return client;
	}

	public static String getRuleApp(String name, String srNo) throws ClientProtocolException, IOException,
			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("呼叫取得RuleApp保存檔API...");
		// delay test(1s=1000, on=1/off=0)
		testDelay(300000, 0);
		init();
		CloseableHttpClient client = getClient(user_DC, password_DC);
		String dsId = getDecisioServiceId(client, name);
		logger.info("取得決策服務ID成功！");
		String dpId = getDeploymentId(client, dsId);
		logger.info("取得部屬ID成功！");
		String blId = getBaselineId(client, dsId);
		logger.info("取得UAT分支ID成功！");

		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
//		HttpGet httpGet = new HttpGet("https://" + ip_DC + ":9443/decisioncenter-api/v1/deployments/" + dpId
//				+ "/download?baselineId=" + blId + "&includeXOMInArchive=true");
		HttpGet httpGet = new HttpGet(dcIP + "/decisioncenter-api/v1/deployments/" + dpId
				+ "/download?baselineId=" + blId + "&includeXOMInArchive=true");

		httpGet.setHeader("Accept", "application/octet-stream");
		HttpResponse httpResponse = client.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();
		File file = new File("D:\\OdmTemp\\" + srNo + "\\" + name + "App.jar");
		FileOutputStream fos = new FileOutputStream(file);
		httpEntity.writeTo(fos);
		fos.flush();
		fos.close();
		Model.Response response = new Model.Response("000", "取得差異清單、差異報表和RuleApp保存檔成功！");
		logger.info("取得差異清單、差異報表和RuleApp保存檔成功！");
		ObjectMapper objectMapper = new ObjectMapper();
		String resultJSON = objectMapper.writeValueAsString(response);
		return resultJSON;
	}

	public static String getDecisioServiceId(CloseableHttpClient client, String name)
			throws ClientProtocolException, IOException {
		logger.info("取得決策服務ID...");

		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
		HttpGet httpGet = new HttpGet(dcIP + "/decisioncenter-api/v1/decisionservices");
//		HttpGet httpGet = new HttpGet("https://" + ip_DC + ":9446/decisioncenter-api/v1/decisionservices");

		httpGet.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		JsonNode elementNode = rootNode.path("elements");
		Iterator<JsonNode> iterator = elementNode.elements();
		String id = "";
		while (iterator.hasNext()) {
			JsonNode element = iterator.next();
			String projectName = element.path("name").asText();
			if (projectName.equals(name)) {
				id = element.path("id").asText();
				break;
			}
		}
		return id;
	}

	public static String getDeploymentId(CloseableHttpClient client, String id)
			throws ClientProtocolException, IOException {
		logger.info("取得部屬ID...");

		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
//		HttpGet httpGet = new HttpGet(
//				"https://" + dcIP + ":9443/decisioncenter-api/v1/decisionservices/" + id + "/deployments");
		HttpGet httpGet = new HttpGet(dcIP + "/decisioncenter-api/v1/decisionservices/" + id + "/deployments");

		httpGet.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		String deploymentId = rootNode.path("elements").get(0).path("id").asText();
		return deploymentId;
	}

	public static String getBaselineId(CloseableHttpClient client, String id)
			throws ClientProtocolException, IOException {
		logger.info("取得分支ID...");

		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
//		HttpGet httpGet = new HttpGet(
//				"https://" + dcIP + ":9443/decisioncenter-api/v1/decisionservices/" + id + "/branches");
		HttpGet httpGet = new HttpGet(dcIP + "/decisioncenter-api/v1/decisionservices/" + id + "/branches");

		httpGet.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		JsonNode elementNode = rootNode.path("elements");
		Iterator<JsonNode> iterator = elementNode.elements();
		String baselineId = "";
		while (iterator.hasNext()) {
			JsonNode element = iterator.next();
			String projectName = element.path("name").asText();
			if (projectName.equals("UAT")) {
				baselineId = element.path("id").asText();
				break;
			}
		}
		return baselineId;
	}

	public static String deployRuleApp(String OUTPUT_FILE_PATH, String goal) throws ClientProtocolException,
			IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("呼叫部屬RuleApp保存檔API...");
		init();
		CloseableHttpClient client = getClient(user_RES, password_RES);
		File deployFile = null;
		File outputDirFile = new File(OUTPUT_FILE_PATH);
		File[] outputFiles = outputDirFile.listFiles();
		for (File f : outputFiles) {
			if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
				deployFile = f;
				logger.info("Deploy file: " + deployFile);
				break;
			}
		}
		HttpPost httpPost;
		if (goal.equals("UAT")) {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			httpPost = new HttpPost(resUATIP + "/res/api/v1/ruleapps");
			System.out.println(httpPost);
			// localhost can change to UAT IP
			// httpPost = new HttpPost("https://" + ip_resUAT +
			// ":9447/res/api/v1/ruleapps");
		} else {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			httpPost = new HttpPost(resPRODIP + "/res/api/v1/ruleapps");
			// localhost can change to Prod IP
			// httpPost = new HttpPost("https://" + ip_resProd +
			// ":9447/res/api/v1/ruleapps");
		}
		httpPost.setHeader("Content-type", "application/octet-stream");
		httpPost.setHeader("Accept", "application/json");
		httpPost.setEntity(new FileEntity(deployFile));
		HttpResponse httpResponse = client.execute(httpPost);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		String resultJSON = "";
		Model.Response response = null;
		JsonNode resourceNode = rootNode.path("resource");
		Iterator<JsonNode> iterator = resourceNode.elements();
		String libraryPath = "";
		while (iterator.hasNext()) {
			JsonNode resource = iterator.next();
			String operationType = resource.path("operationType").asText();
			if (operationType.equals("CHANGE_VERSION_AND_ADD")) {
				libraryPath = resource.path("managedXomGeneratedProperty").asText();
				break;
			}
		}
		libraryPath = libraryPath.substring(libraryPath.indexOf(":") + 1, libraryPath.length() - 1);
		if (rootNode.path("succeeded").asText().equals("true")) {
			try {
				unZip(deployFile, outputDirFile);
			} catch (Exception e) {
				deleteRuleApp_Prod(deployFile.getName().substring(0, deployFile.getName().indexOf(".")));
				response = new Model.Response("005", "解壓縮檔案失敗！" + getErrorMsg(e));
				logger.error("解壓縮檔案失敗！");
				logger.error(e.getMessage());
				resultJSON = objectMapper.writeValueAsString(response);
				return resultJSON;
			}
			if (goal.equals("Prod")) {
				String xomFilePath = OUTPUT_FILE_PATH + "\\"
						+ deployFile.getName().substring(0, deployFile.getName().indexOf("."));
				File xomFileDir = new File(xomFilePath);
				File[] files = xomFileDir.listFiles();
				File xomFile = null;
				for (File f : files) {				
					if (f.getName().toLowerCase().endsWith(".jar")) {
						xomFile = f;
						break;
					}
				}
				String xomUri = deployXom(xomFile, resPRODIP);
				if (xomUri.contains("\"succeeded\" : false")) {
					response = new Model.Response("005", "部屬XOM檔案失敗！" + xomUri);
					resultJSON = objectMapper.writeValueAsString(response);
					return resultJSON;
				}
				String updateMessage = updateLibrary(libraryPath, xomUri, resPRODIP);
				if (!updateMessage.equals("")) {
					response = new Model.Response("005", "更新RuleApp屬性失敗！" + updateMessage);
					resultJSON = objectMapper.writeValueAsString(response);
					return resultJSON;
				}
				String testMessage = testDeployRuleApp(rootNode, resPRODIP);
				if (testMessage.equals("")) {
					response = new Model.Response("000", "部屬RuleApp保存檔至Prod成功！");
					logger.info("部屬RuleApp保存檔至Prod成功！");
					resultJSON = objectMapper.writeValueAsString(response);
					deleteFile(outputDirFile);
					logger.info("檔案已刪除！");
				} else {
					response = new Model.Response("005", "部屬RuleApp保存檔至Prod失敗！" + testMessage);
					resultJSON = objectMapper.writeValueAsString(response);
				}
			} else {
				String xomFilePath = OUTPUT_FILE_PATH + "\\"
						+ deployFile.getName().substring(0, deployFile.getName().indexOf("."));
				File xomFileDir = new File(xomFilePath);
				File[] files = xomFileDir.listFiles();
				File xomFile = null;
				for (File f : files) {
					if (f.getName().toLowerCase().endsWith(".jar")) {
						xomFile = f;
						break;
					}
				}
				String xomUri = deployXom(xomFile, resUATIP);
				if (xomUri.contains("\"succeeded\" : false")) {
					response = new Model.Response("005", "部屬XOM檔案失敗！" + xomUri);
					resultJSON = objectMapper.writeValueAsString(response);
					return resultJSON;
				}
				String updateMessage = updateLibrary(libraryPath, xomUri, resUATIP);
				if (!updateMessage.equals("")) {
					response = new Model.Response("005", "更新RuleApp屬性失敗！" + updateMessage);
					resultJSON = objectMapper.writeValueAsString(response);
					return resultJSON;
				}
				String testMessage = testDeployRuleApp(rootNode, resUATIP);
				if (testMessage.equals("")) {
					response = new Model.Response("000", "部屬RuleApp保存檔至UAT成功！");
					logger.info("部屬RuleApp保存檔至UAT成功！");
					resultJSON = objectMapper.writeValueAsString(response);
				} else {
					response = new Model.Response("005", "部屬RuleApp保存檔至UAT失敗！" + testMessage);
					resultJSON = objectMapper.writeValueAsString(response);
				}
			}
		} else {
			if (goal.equals("Prod")) {
				response = new Model.Response("005", "部屬RuleApp保存檔至Prod失敗！");
				logger.error("部屬RuleApp保存檔至Prod失敗！");
				logger.error(httpEntityStr);
				resultJSON = objectMapper.writeValueAsString(response);
			} else {
				response = new Model.Response("005", "部屬RuleApp保存檔至UAT失敗！");
				logger.error("部屬RuleApp保存檔至UAT失敗！");
				logger.error(httpEntityStr);
				resultJSON = objectMapper.writeValueAsString(response);
			}
		}
		return resultJSON;
	}

	public static String deployRuleAppProd(String OUTPUT_FILE_PATH) throws ClientProtocolException, IOException,
			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("呼叫部屬RuleApp保存檔API...");
		init();
		CloseableHttpClient client = getClient(user_RES, password_RES);
		File deployFile = null;
		File outputDirFile = new File(OUTPUT_FILE_PATH);
		File[] outputFiles = outputDirFile.listFiles();
		for (File f : outputFiles) {
			if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
				deployFile = f;
				logger.info("Deploy file: " + deployFile);
				break;
			}
		}

		String resultJSON = "";
		Map<String, String> allResultMap = new HashMap<>();
		
		for (String ipProd : ipProd) {
			HttpPost httpPost;
			httpPost = new HttpPost(ipProd + "/res/api/v1/ruleapps");

			httpPost.setHeader("Content-type", "application/octet-stream");
			httpPost.setHeader("Accept", "application/json");
			httpPost.setEntity(new FileEntity(deployFile));
			HttpResponse httpResponse = client.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode rootNode = objectMapper.readTree(httpEntityStr);
			JsonNode resourceNode = rootNode.path("resource");
			Iterator<JsonNode> iterator = resourceNode.elements();

			String libraryPath = "";

			while (iterator.hasNext()) {
				JsonNode resource = iterator.next();
				String operationType = resource.path("operationType").asText();
				if (operationType.equals("CHANGE_VERSION_AND_ADD")) {
					libraryPath = resource.path("managedXomGeneratedProperty").asText();
					break;
				}
			}
			
			libraryPath = libraryPath.substring(libraryPath.indexOf(":") + 1, libraryPath.length() - 1);
			Map<String, String> currentResultMap = new HashMap<>();
			
			if (rootNode.path("succeeded").asText().equals("true")) {
				try {
					unZip(deployFile, outputDirFile);
				} catch (Exception e) {
					deleteRuleApp(deployFile.getName().substring(0, deployFile.getName().indexOf(".")));
					currentResultMap.put("005", ipProd + " 解壓縮檔案失敗！" + getErrorMsg(e));
					logger.error("解壓縮檔案失敗！" + e.getMessage());
				}
				String xomFilePath = OUTPUT_FILE_PATH + "\\"
						+ deployFile.getName().substring(0, deployFile.getName().indexOf("."));
				File xomFileDir = new File(xomFilePath);
				File[] files = xomFileDir.listFiles();
				File xomFile = null;
				for (File f : files) {
					if (f.getName().toLowerCase().endsWith(".jar")) {
						xomFile = f;
						break;
					}
				}

				String xomUri = deployXom(xomFile, ipProd);
				if (xomUri.contains("\"succeeded\" : false")) {
					currentResultMap.put("005", ipProd + " 部屬XOM檔案失敗！" + xomUri);
					
				}

				String updateMessage = updateLibrary(libraryPath, xomUri, ipProd);
				if (!updateMessage.equals("")) {
					currentResultMap.put("005", ipProd + " 更新RuleApp屬性失敗！" + updateMessage);
				}

				String testMessage = testDeployRuleApp(rootNode, ipProd);
				if (testMessage.equals("")) {
					currentResultMap.put("000", ipProd + " 部屬RuleApp保存檔至Prod成功！");
					logger.info("ip: " + ipProd + ", 部屬RuleApp保存檔至Prod成功！");
				} else {
					currentResultMap.put("005", ipProd + " 部屬RuleApp保存檔至Prod失敗！" + testMessage);
				}

			} else {
				currentResultMap.put("005", ipProd + " 部屬RuleApp保存檔至Prod失敗！");
				logger.error("ip: " + ipProd + ", 部屬RuleApp保存檔至Prod失敗！");
				logger.error(httpEntityStr);
			}
			allResultMap.putAll(currentResultMap);
		}
		try {
			deleteFile(outputDirFile);
			logger.info("檔案已刪除！");
		} catch (Exception e) {
			logger.error("刪除檔案失敗!" + e.getMessage());
		}
		resultJSON = new ObjectMapper().writeValueAsString(allResultMap);
		
		return resultJSON;
	}

	public static void unZip(File zipFile, File targetFolder) throws IOException {
		String fileName = zipFile.getName();
		String unZipRootFolderName = fileName.substring(0, fileName.indexOf("."));
		ZipInputStream zIn = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
		ZipEntry zipEntry = null;
		FileOutputStream fOut = null;
		try {
			File unZipRootFolder = new File(targetFolder + "/" + unZipRootFolderName);
			if (!unZipRootFolder.exists()) {
				unZipRootFolder.mkdirs();
			}
			while ((zipEntry = zIn.getNextEntry()) != null) {
				if (!zipEntry.isDirectory()) {
					File targerFile = new File(
							targetFolder.getPath() + "/" + unZipRootFolderName + "/" + zipEntry.getName());
					File parent = targerFile.getParentFile();
					if (!parent.exists()) {
						parent.mkdirs();
					}
					fOut = new FileOutputStream(targerFile);
					int byte_no;
					byte[] b1 = new byte[64];
					while ((byte_no = zIn.read(b1)) > 0) {
						fOut.write(b1, 0, byte_no);
					}
					fOut.close();
				}
			}
		} finally {
			if (fOut != null) {
				fOut.close();
			}
			if (zIn != null) {
				zIn.close();
			}
		}
	}

	public static String deployXom(File xomFile, String ip) throws ClientProtocolException, IOException,
			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		CloseableHttpClient client = ApiCaller.getClient("resAdmin", "resAdmin");
		String xomName = xomFile.getName().substring(0, xomFile.getName().indexOf("_"));

		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
		HttpPost httpPost = new HttpPost(ip + "/res/api/v1/xoms/" + xomName + ".jar");
		// HttpPost httpPost = new HttpPost("https://" + ip +
		// ":9447/res/api/v1/xoms/"+xomName+".jar");

		httpPost.setHeader("Content-type", "application/octet-stream");
		httpPost.setHeader("Accept", "application/json");
		httpPost.setEntity(new FileEntity(xomFile));
		HttpResponse httpResponse = client.execute(httpPost);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		String xomUri = "";
		if (rootNode.path("succeeded").asText().equals("true")) {
			xomUri = rootNode.path("resource").path("uri").asText();
			logger.info("部屬XOM檔案成功！");
			logger.info(httpEntityStr);
		} else {
			xomUri = httpEntityStr;
			logger.error("部屬XOM檔案失敗！");
			logger.error(httpEntityStr);
		}
		return xomUri;
	}

	public static String updateLibrary(String libraryPath, String xomUri, String ip) throws KeyManagementException,
			NoSuchAlgorithmException, KeyStoreException, ClientProtocolException, IOException {
		CloseableHttpClient client = ApiCaller.getClient("resAdmin", "resAdmin");
		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
		HttpPut httpPut = new HttpPut(ip + "/res/api/v1/libraries" + libraryPath);
		// HttpPut httpPut = new HttpPut("https://" + ip + ":9447/res/api/v1/libraries"
		// + libraryPath);
		httpPut.setHeader("Content-type", "text/plain");
		httpPut.setHeader("Accept", "application/json");
		httpPut.setEntity(new StringEntity(xomUri));
		HttpResponse httpResponse = client.execute(httpPut);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		System.out.println("httpEntityStr: " + httpEntityStr);
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		String updateMessage = "";
		if (rootNode.path("succeeded").asText().equals("true")) {
			logger.info("更新RuleApp屬性成功！");
			logger.info(httpEntityStr);
		} else {
			updateMessage = httpEntityStr;
			logger.error("更新RuleApp屬性失敗！");
			logger.error(httpEntityStr);
		}

		return updateMessage;
	}

	
	public static String testDeployRuleApp(JsonNode rootNode, String ip) throws KeyManagementException,
			NoSuchAlgorithmException, KeyStoreException, ClientProtocolException, IOException {
		JsonNode resourceNode = rootNode.path("resource");
		Iterator<JsonNode> iterator = resourceNode.elements();
		String resultPath = "";
		while (iterator.hasNext()) {
			JsonNode resource = iterator.next();
			String operationType = resource.path("operationType").asText();
			if (operationType.equals("CHANGE_VERSION_AND_ADD")) {
				resultPath = resource.path("resultPath").asText();
				break;
			}
		}
		String testMessage = "";
		if (!resultPath.equals("")) {
			CloseableHttpClient client = getClient(user_RES, password_RES);

			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			HttpGet httpGet = new HttpGet(ip + "/DecisionService/rest" + resultPath + "/json");
//			HttpGet httpGet = new HttpGet("https://" + ip + ":9447/DecisionService/rest" + resultPath + "/json");
			httpGet.setHeader("Accept", "application/json");
			HttpResponse httpResponse = client.execute(httpGet);
			HttpEntity httpEntity = httpResponse.getEntity();
			String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
			client.close();
			client = getClient(user_RES, password_RES);
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			HttpPost httpPost = new HttpPost(ip + "/DecisionService/rest" + resultPath);
//			HttpPost httpPost = new HttpPost("https://" + ip + ":9447/DecisionService/rest" + resultPath);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			httpPost.setEntity(new StringEntity(httpEntityStr));
			httpResponse = client.execute(httpPost);
			httpEntity = httpResponse.getEntity();
			httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				logger.info("測試部屬的RuleApp成功");
			} else {
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode testNode = objectMapper.readTree(httpEntityStr);
				testMessage = testNode.path("message").asText();
				logger.error("測試部屬的RuleApp失敗");
				logger.error(httpEntityStr);
			}
		}

		return testMessage;

	}

	public static String deleteRuleApp(String projectName) throws ClientProtocolException, IOException,
			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("呼叫退版Prod API...");
		init();
		CloseableHttpClient client = getClient(user_RES, password_RES);
		String ruleAppName = projectName + "App";
		String ruleAppId = getRuleAppId(client, ruleAppName, "Prod");
		String ruleSetId = getRuleSetId(client, ruleAppId, "Prod");

		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
		HttpDelete httpDelete = new HttpDelete(resPRODIP + "/res/api/v1/ruleapps/" + ruleSetId);
		httpDelete.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpDelete);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		String resultJSON = "";
		if (rootNode.path("succeeded").asText().equals("true")) {
			Model.Response response = new Model.Response("000", "退版Prod成功！");
			logger.info("退版Prod成功！");
			resultJSON = objectMapper.writeValueAsString(response);
		} else {
			Model.Response response = new Model.Response("006", "退版Prod失敗！");
			logger.error("退版Prod失敗！");
			logger.error(httpEntityStr);
			resultJSON = objectMapper.writeValueAsString(response);
		}
		return resultJSON;
	}

	public static String deleteRuleApp_Prod(String projectName) throws ClientProtocolException, IOException,
			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("呼叫退版Prod API...");
		init();
		CloseableHttpClient client = getClient(user_RES, password_RES);
		String ruleAppName = projectName + "App";
		String ruleAppId = getRuleAppId(client, ruleAppName, "Prod");
		String ruleSetId = getRuleSetId(client, ruleAppId, "Prod");

		String resultJSON = "";
		for (String ip : ipProd) {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			HttpDelete httpDelete = new HttpDelete(ip + "/res/api/v1/ruleapps/" + ruleSetId);
			httpDelete.setHeader("Accept", "application/json");
			HttpResponse httpResponse = client.execute(httpDelete);
			HttpEntity httpEntity = httpResponse.getEntity();
			String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode rootNode = objectMapper.readTree(httpEntityStr);
			
			Map<String, String> resultMap = new HashMap<>();
			if (rootNode.path("succeeded").asText().equals("true")) {
				resultMap.put("000", "Ip: " + ip + "退版Prod成功！");
				resultJSON = objectMapper.writeValueAsString(resultMap);
				logger.info("Ip: " + ip + "退版Prod成功！");
			} else {
				resultMap.put("006", "Ip: " + ip + "退版Prod失敗！");
				resultJSON = objectMapper.writeValueAsString(resultMap);
				logger.error("Ip: " + ip + "退版Prod失敗！");
				logger.error(httpEntityStr);
			}
		}
		return resultJSON;
	}

	public static String deleteRuleApp_UAT(String projectName, String OUTPUT_FILE_PATH) throws ClientProtocolException,
			IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("呼叫退版UAT API...");
		init();
		File outputDirFile = new File(OUTPUT_FILE_PATH);
		ObjectMapper objectMapper = new ObjectMapper();
		String resultJSON = "";
		if (!outputDirFile.exists()) {
			Model.Response response = new Model.Response("002", "參數錯誤，請檢查SR單號檔案路徑：" + OUTPUT_FILE_PATH + "是否存在！");
			logger.error("參數錯誤，請檢查SR單號檔案路徑：" + OUTPUT_FILE_PATH + "是否存在！");
			resultJSON = objectMapper.writeValueAsString(response);
			return resultJSON;
		}
		CloseableHttpClient client = getClient(user_RES, password_RES);
		String ruleAppName = projectName + "App";
		String ruleAppId = getRuleAppId(client, ruleAppName, "UAT");
		logger.info("取得Rule App ID成功！");
		String ruleSetId = getRuleSetId(client, ruleAppId, "UAT");
		logger.info("取得Rule Set ID成功！");
		// localhost can change to UAT IP
		// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
		HttpDelete httpDelete = new HttpDelete(resUATIP + "/res/api/v1/ruleapps/" + ruleSetId);
//		HttpDelete httpDelete = new HttpDelete("https://" + ip_resUAT + ":9447/res/api/v1/ruleapps/" + ruleSetId);

		httpDelete.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpDelete);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		File[] outputFiles = outputDirFile.listFiles();
		if (rootNode.path("succeeded").asText().equals("true")) {
			Model.Response response = new Model.Response("000", "退版UAT成功！");
			logger.info("退版UAT成功！");
			resultJSON = objectMapper.writeValueAsString(response);
			for (File f : outputFiles) {
				f.delete();
			}
			outputDirFile.delete();
			logger.info("檔案已刪除！");
		} else {
			Model.Response response = new Model.Response("006", "退版UAT失敗！");
			logger.error("退版UAT失敗！");
			logger.error(httpEntityStr);
			resultJSON = objectMapper.writeValueAsString(response);
		}
		return resultJSON;
	}

	public static String getRuleAppId(CloseableHttpClient client, String ruleAppName, String goal)
			throws ClientProtocolException, IOException {
		logger.info("取得RuleApp ID...");
		HttpGet httpGet = null;
		if (goal.equals("UAT")) {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			httpGet = new HttpGet(resUATIP + "/res/api/v1/ruleapps/" + ruleAppName + "/highest?parts=none");
//			httpGet = new HttpGet(
//					"https://" + ip_resUAT + ":9447/res/api/v1/ruleapps/" + ruleAppName + "/highest?parts=none");
		} else {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			httpGet = new HttpGet(resPRODIP + "/res/api/v1/ruleapps/" + ruleAppName + "/highest?parts=none");
//			httpGet = new HttpGet(
//					"https://" + ip_resProd + ":9447/res/api/v1/ruleapps/" + ruleAppName + "/highest?parts=none");
		}
		httpGet.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		String ruleAppId = rootNode.path("id").asText();
		return ruleAppId;
	}

	public static String getRuleSetId(CloseableHttpClient client, String ruleAppId, String goal)
			throws ParseException, IOException {
		logger.info("取得RuleSet ID...");
		HttpGet httpGet = null;
		if (goal.equals("UAT")) {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			httpGet = new HttpGet(resUATIP + "/res/api/v1/ruleapps/" + ruleAppId + "/rulesets?parts=version");
//			httpGet = new HttpGet(
//					"https://" + ip_resUAT + ":9447/res/api/v1/ruleapps/" + ruleAppId + "/rulesets?parts=version");
		} else {
			// 20241216 modify by Peter Liao : Elvis 說 9446 and 9447 都改成 9443 port
			httpGet = new HttpGet(resPRODIP + "/res/api/v1/ruleapps/" + ruleAppId + "/rulesets?parts=version");
//			httpGet = new HttpGet(
//					"https://" + ip_resProd + ":9447/res/api/v1/ruleapps/" + ruleAppId + "/rulesets?parts=version");
		}
		httpGet.setHeader("Accept", "application/json");
		HttpResponse httpResponse = client.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();
		String httpEntityStr = EntityUtils.toString(httpEntity, "UTF-8");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(httpEntityStr);
		Iterator<JsonNode> iterator = rootNode.elements();
		int maxVersionNo = 0;
		int maxIndex = 0;
		int i = 0;
		while (iterator.hasNext()) {
			JsonNode ruleset = iterator.next();
			String version = ruleset.path("version").asText();
			if (Integer.parseInt(version.substring(version.indexOf(".") + 1)) > maxVersionNo) {
				maxVersionNo = Integer.parseInt(version.substring(version.indexOf(".") + 1));
				maxIndex = i;
			}
			i++;
		}
		String ruleSetId = rootNode.get(maxIndex).path("id").asText();
		return ruleSetId;

	}

	public static String getErrorMsg(Exception e) {
		String errorMsg = e.toString() + ":" + e.getMessage() + "/r/n";
		StackTraceElement[] trace = e.getStackTrace();
		for (StackTraceElement s : trace) {
			errorMsg += s + "/r/n";
		}
		return errorMsg;

	}

	public static void testDelay(int time, int enable) {
		if (enable == 1) {
			System.out.println("Delay: " + time / 1000 / 60 + " min");
			System.out.println("In testDelay：" + new Date());

			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {
			}

			System.out.println("End testDelay：" + new Date() + "\n");
		}
	}

	public static void deleteFile(File file) {
		if (file.exists()) {
			if (file.isFile()) {
				file.delete();
			} else {
				File[] listFiles = file.listFiles();
				for (File file2 : listFiles) {
					deleteFile(file2);
				}
			}
			file.delete();
		} else {
			System.out.println("該file路徑不存在！！");
		}
	}

}

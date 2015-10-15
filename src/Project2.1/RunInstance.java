import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

public class RunInstance {
	public static void CreateSecurity(AmazonEC2Client amazonEC2Client, String keyname) {
		// Create Key Pair
		CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
		createKeyPairRequest.withKeyName(keyname);
		CreateKeyPairResult createKeyPairResult = amazonEC2Client.createKeyPair(createKeyPairRequest);
		KeyPair keyPair = new KeyPair();
		keyPair = createKeyPairResult.getKeyPair();
		String privateKey = keyPair.getKeyMaterial();

		// Create a Security Group
//		CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
//		csgr.withGroupName(securitygroup).withDescription("My security group");
//		CreateSecurityGroupResult createSecurityGroupResult = amazonEC2Client.createSecurityGroup(csgr);
//		IpPermission ipPermission = new IpPermission();
//		ipPermission.withIpRanges("0.0.0.0/0").withIpProtocol("all");
//		AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest();
//		authorizeSecurityGroupIngressRequest.withGroupName("15619securitygroup").withIpPermissions(ipPermission);
//		amazonEC2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
	}

	public static Instance CreateInstance(AmazonEC2Client amazonEC2Client, String ami, String type, int i) {
		if (i == 0) {
			try {
				// Create key pair and a security group
				CreateSecurity(amazonEC2Client, "15619key");
			} catch (com.amazonaws.AmazonServiceException e) {
			}
		}
		// Create Instance Request
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		// Configure Instance Request
		runInstancesRequest.withImageId(ami).withInstanceType(type).withMinCount(1).withMaxCount(1)
				.withKeyName("15619key").withSecurityGroups("Projects");

		// Launch Instance
		RunInstancesResult runInstancesResult = amazonEC2Client.runInstances(runInstancesRequest);
		// Return the Object Reference of the Instance just Launched
		Instance instance = runInstancesResult.getReservation().getInstances().get(0);

		// Add a Tag to the Instance
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources(instance.getInstanceId()).withTags(new Tag("Project", "2.1"));
		amazonEC2Client.createTags(createTagsRequest);

		// Print InstanceID
		System.out.println("Just launched an Instance with ID:" + instance.getInstanceId());

		// avoid create two instances in 100 seconds
		try {
			Thread.sleep(100000);
		} catch (InterruptedException ie) {
			// Handle the exception
		}
		return instance;
	}

	public static HttpURLConnection ConnectURL(String url_info) throws IOException {
		//connect URL 
		URL url = new URL(url_info);
		HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
		urlcon.connect();
		// ensure connect successfully
		BufferedReader bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
		while (bufferRead.readLine() == null) {
			urlcon = (HttpURLConnection) url.openConnection();
			urlcon.connect();
			bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
		}
		System.out.println("Connect URL succeed");
		return urlcon;
	}

	public static String getDNS(AmazonEC2Client amazonEC2Client, String InstanceId) {
		// Obtain a list of Reservations
		List<Reservation> reservations = amazonEC2Client.describeInstances().getReservations();
		for (Reservation reservation : reservations) {
			for (Instance instance : reservation.getInstances()) {
				if (instance.getState().getName().equals("running") & instance.getInstanceId().equals(InstanceId)) {
					return instance.getPublicDnsName();
				}
			}
		}
		return null;
	}

	public static double getRPS(String url_info) {
		// get rps
		String log = "";
		boolean flag = true;
		double RPS = 0;
		Pattern pattern = Pattern.compile("ec2[^=]*=([.\\d]*)");
		while (flag) {
			try {
				// give a delay
				Thread.sleep(20000);
				URL url = new URL(url_info);
				HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
				urlcon.connect();
				// read all lines in test.log
				BufferedReader bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
				String Line = "";
				while ((Line = bufferRead.readLine()) != null) 
		            log+= Line;
				// read rps data of last minute
				log = log.substring(log.lastIndexOf("[Minute"));
				Matcher matcher = pattern.matcher(log);
				while (matcher.find()) {
					RPS += Double.parseDouble(matcher.group(1)); // sum the total rps
				}
				bufferRead.close();
				flag = false;
			}catch(StringIndexOutOfBoundsException e) {
				// e.printStackTrace();
			}
			catch (IOException e) {
				// e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
			}
		}
		System.out.println("RPS:" + RPS);
		return RPS;
	}

	public static String getTestid(String url_info) throws InterruptedException {
		boolean flag = true;
		String testid = "";
		String userResponse = "";
		try {
			URL url = new URL(url_info);
			HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
			urlcon.connect();
			Thread.sleep(100000);	
			BufferedReader bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));	
			while ((userResponse = bufferRead.readLine() )== null) {
				urlcon = (HttpURLConnection) url.openConnection();
				urlcon.connect();
				bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
			}
			System.out.println("userResponse:"+userResponse);
			String[] retval = userResponse.split("\\.");
			testid = retval[1];
			bufferRead.close();
			flag = false;
		} catch (IOException e) {
			// e.printStackTrace();
		}
		System.out.println("testid:" + testid);
		return testid;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		// Load the Properties File with AWS Credentials
		Properties properties = new Properties();
		properties.load(RunInstance.class.getResourceAsStream("/AwsCredentials.properties"));
		BasicAWSCredentials bawsc = new BasicAWSCredentials(properties.getProperty("accessKey"),
				properties.getProperty("secretKey"));
		// Create an Amazon EC2 Client
		AmazonEC2Client amazonEC2Client = new AmazonEC2Client(bawsc);
		List<String> Id = new ArrayList<String>();
		List<String> DNS = new ArrayList<String>();
		// Create Load Generator and get its DNS
		Instance LoadGenerator = CreateInstance(amazonEC2Client, "ami-4389fb26", "m3.medium", 0);
		Id.add(LoadGenerator.getInstanceId());
		DNS.add(getDNS(amazonEC2Client, Id.get(0)));
		System.out.println("The DNS of Load Generator is:" + DNS.get(0));

		// Create Data Center
		int index = 1;
		Instance DataCenter = CreateInstance(amazonEC2Client, "ami-abb8cace", "m3.medium", index);
		Id.add(DataCenter.getInstanceId());
		DNS.add(getDNS(amazonEC2Client, Id.get(index)));
		System.out.println("The DNS of Data Center[" + index + "] is:" + DNS.get(index));

		// Connect to Load Generator,its DNS is the first of DNS lists
		ConnectURL("http://" + DNS.get(0) + "/password?passwd=bFFR5T8Hw2VTczO7HnVhVm6WeKHWtN8f");
		System.out.println("http://" + DNS.get(0) + "/password?passwd=bFFR5T8Hw2VTczO7HnVhVm6WeKHWtN8f");
		
		// Submit the first data center instance's DNS, start the first test
		String testid = getTestid("http://" + DNS.get(0) + "/test/horizontal?dns=" + DNS.get(index));

		// Calculate RPS
		Double rps = getRPS("http://" + DNS.get(0) + "/log?name=test." + testid + ".log");
		while (rps < 4000) {
			index += 1;
			Id.add(CreateInstance(amazonEC2Client, "ami-abb8cace", "m3.medium", index).getInstanceId());
			DNS.add(getDNS(amazonEC2Client, Id.get(index)));
			System.out.println("The DNS of Data Center[" + index + "] is:" + DNS.get(index));
			Thread.sleep(100000);
			URL url = new URL("http://" + DNS.get(0) + "/test/horizontal/add?dns=" + DNS.get(index));
			HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
			urlcon.connect();
			BufferedReader bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
			while (bufferRead.readLine() == null) {
				urlcon = (HttpURLConnection) url.openConnection();
				urlcon.connect();
				bufferRead = new BufferedReader(new InputStreamReader(urlcon.getInputStream()));
			}
			System.out.println("add new instances to test succeed");
			rps = getRPS("http://" + DNS.get(0) + "/log?name=test." + testid + ".log");
		}

	}
}

package seminar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.SecurityGroup;

public class StartEC2 {

	private static final Logger logger = Logger.getLogger(StartEC2.class.getName());

	private static final String IMAGE_ID_UBUNTU = "ami-07ebfd5b3428b6f4d";
	private static final String SECURITY_GROUP_NAME = "assignment4";
	private static final String KEY_PAIR_NAME = "assignment4";

	public static void main(String[] args) {

		final AmazonEC2 ec2Client = createStandardEC2Client();

		logger.log(Level.INFO, "EC2 client initialized");

		listAllAvailabilityZones(ec2Client);

		listAMIsFiltered(ec2Client);

		final String securityGroupId = ensureSecurityGroup(ec2Client);
		final List<IpPermission> permissions = Arrays.asList(
				createIpPermission(22),
				createIpPermission(80)
		);
		authorizeSecurityGroupIngressRequest(ec2Client, securityGroupId, permissions);

		ensureKeyPair(ec2Client);

	}

	/**
	 * Creates a default {@code AmazonEC2} client with {@code Regions.US_WEST_1} and
	 * the standard credentials loaded from ~/.aws/credentials.
	 * @return
	 */
	@Nonnull
	private static AmazonEC2 createStandardEC2Client() {
		return AmazonEC2ClientBuilder.standard()
				.withRegion(Regions.US_EAST_1)
				.build();
	}

	/**
	 * List all available zones for given EC2 {@code client}
	 * @param client Amazon EC2 client
	 */
	private static void listAllAvailabilityZones(@Nonnull final AmazonEC2 client) {
		final DescribeAvailabilityZonesResult result = client.describeAvailabilityZones();

		for (final AvailabilityZone zone : result.getAvailabilityZones()) {
			final String message = "Zone " +
					zone.getZoneName() +
					" with status " +
					zone.getState() +
					" in region " +
					zone.getRegionName();

			logger.log(Level.INFO, message);
		}
	}

	/**
	 * List all AMIs filterd by the windows platform.
	 * @param client Amazon EC2 client
	 */
	private static void listAMIsFiltered(@Nonnull final AmazonEC2 client) {
		// Ubuntu Server 18.04 LTS (HVM), SSD Volume Type x64
		final Filter amiFilter = new Filter("image-id", Collections.singletonList(IMAGE_ID_UBUNTU));
		final DescribeImagesRequest request = new DescribeImagesRequest()
				.withFilters(amiFilter);

		final DescribeImagesResult result = client.describeImages(request);

		for (final Image image : result.getImages()) {
			final String message = "Image " +
					image.getName() +
					" with id " +
					image.getImageId() +
					" on platform " +
					image.getPlatform();

			logger.log(Level.INFO, message);
		}
	}

	/**
	 * Get or create a security group with name {@code SECURITY_GROUP}
	 * @param client Amazon EC2 client
	 * @return Id of security group
	 */
	@Nonnull
	private static String ensureSecurityGroup(@Nonnull final AmazonEC2 client) {
		final DescribeSecurityGroupsResult result = client.describeSecurityGroups();

		final Optional<SecurityGroup> group = result.getSecurityGroups()
				.stream()
				.filter(g -> SECURITY_GROUP_NAME.equals(g.getGroupName()))
				.findFirst();

		if (group.isPresent()) {
			final String groupId = group.get().getGroupId();
			logger.log(Level.INFO, "Security group found with id {0}", groupId);

			return groupId;
		}

		final CreateSecurityGroupRequest request = new CreateSecurityGroupRequest()
				.withGroupName(SECURITY_GROUP_NAME)
				.withDescription("Assignment 4 security group");
		final CreateSecurityGroupResult createResult = client.createSecurityGroup(request);

		logger.log(Level.INFO, "Created new security group with id {0}", createResult.getGroupId());

		return createResult.getGroupId();
	}

	/**
	 * Create an ip permission that allows access to all ips with tcp protocol and port 22
	 * @return IP permission
	 */
	private static IpPermission createIpPermission(final int port) {
		final IpRange ipRange = new IpRange().withCidrIp("0.0.0.0/0");

		return new IpPermission()
				.withIpv4Ranges(ipRange)
				.withIpProtocol("tcp")
				.withFromPort(port)
				.withToPort(port);
	}

	private static void authorizeSecurityGroupIngressRequest(
			@Nonnull final AmazonEC2 client,
			@Nonnull final String securityGroupId,
			@Nonnull final List<IpPermission> ipPermissions
	) {
		final AuthorizeSecurityGroupIngressRequest request = new AuthorizeSecurityGroupIngressRequest()
				.withGroupId(securityGroupId)
				.withIpPermissions(ipPermissions);

		try {
			client.authorizeSecurityGroupIngress(request);

			logger.log(Level.INFO, "Security rule created");
		} catch (final Exception e) {
			logger.log(Level.INFO, "Security rule already exists", e.fillInStackTrace());
		}
	}

	/**
	 * Get or create a new key-pair
	 * @param client Amazon EC2 client
	 * @return Key-pair name
	 */
	@Nonnull
	private static String ensureKeyPair(@Nonnull final AmazonEC2 client) {
		final DescribeKeyPairsRequest request = new DescribeKeyPairsRequest()
				.withKeyNames(KEY_PAIR_NAME);

		final DescribeKeyPairsResult result = client.describeKeyPairs(request);

		if (!result.getKeyPairs().isEmpty()) {
			final KeyPairInfo keyPairInfo = result.getKeyPairs().iterator().next();
			logger.log(Level.INFO, "Existing key-pair found {0}", keyPairInfo);

			return keyPairInfo.getKeyName();
		}

		final CreateKeyPairRequest createRequest = new CreateKeyPairRequest()
				.withKeyName(KEY_PAIR_NAME);
		final CreateKeyPairResult createResult = client.createKeyPair(createRequest);
		final KeyPair keyPair =  createResult.getKeyPair();
		logger.log(Level.INFO, "Created a new key-pair {0}", keyPair);

		writeKeyPairToFileSystem(keyPair);

		return keyPair.getKeyName();
	}


	/**
	 * Writes given {@code keyPair} to user's home directory Documents folder.
	 * @param keyPair Key-pair
	 */
	private static void writeKeyPairToFileSystem(@Nonnull final KeyPair keyPair) {
		final String path = System.getProperty("user.home") + "/Documents";
		final File file = new File(path);

		final Reader reader = new BufferedReader(new StringReader(keyPair.getKeyMaterial()));
		try (final Writer writer = new BufferedWriter(new FileWriter(file))) {
			final char[] buffer = new char[1024];
			int length;

			while ((length = reader.read(buffer)) != -1) {
				writer.write(buffer,0 , length);
			}

			writer.flush();
			reader.close();
			reader.close();

			logger.log(Level.INFO, "Written key-pair material to file {0}", path);
		} catch (final Exception e) {
			logger.log(Level.INFO, "Could not write key-pair material to file {0}", path);
		}
	}
}
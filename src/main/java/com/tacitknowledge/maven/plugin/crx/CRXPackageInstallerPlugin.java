package com.tacitknowledge.maven.plugin.crx;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which installs a CRX package on the target host.
 *
 * @goal post
 * @phase integration-test
 */
public class CRXPackageInstallerPlugin extends AbstractMojo
{
	/**
	 * the connection default timeout is set to 5 seconds.
	 */
	private static final int CONNECTION_DEFAULT_TIMEOUT = 5000;

	public static final String DATE_FORMAT_NOW = "yyyyMMdd-HHmm";

	/**
	 * Whether to skip this step even though it has been configured in the
	 * project to be executed. This property may be set by the
	 * <code>crxpackage.install.skip</code> comparable to the
	 * <code>maven.test.skip</code> property to prevent running the unit tests.
	 * 
	 * @parameter expression="${crxpackage.skip}" default-value="false"
	 * @required
	 */
	private boolean skip;

	/**
	 * Whether to skip the upload and install step even though it has been configured in the
	 * project to be executed.
	 * 
	 * @parameter expression="${crxpackage.install.skip}" default-value="false"
	 * @required
	 */
	private boolean skipInstall;

	/**
	 * Whether to skip the backup.This property may be set by the
	 * <code>crxpackage.backup.enable</code>
	 * 
	 * @parameter expression="${crxpackage.backup.enable}" default-value="false"
	 * @required
	 */
	private boolean enableBackup;

	/**
	 * If set to <code>true</code>. Only backup will be performed.
	 * <code>crxpackage.backup.enable</code>
	 * 
	 * @parameter expression="${crxpackage.backupOnly}" default-value="false"
	 * @required
	 */
	private boolean backupOnly;

	/**
	 * If set to <code>true</code>. We Ignore the ACL when installing the package.
	 * <code>acl.ignore</code>
	 * 
	 * @parameter expression="${acl.ignore}" default-value="false"
	 * @required
	 */
	private boolean aclIgnore;

	/**
	 * The generated backup folder location. This property may be set by the
	 * <code>crxpackage.backup.backupFolder</code>
	 * @parameter expression="${crxpackage.backup.backupFolder}" default-value= "backup"
	 * @required
	 */
	private File backupFolder;

	/**
	 * The name of the generated JAR file.
	 * 
	 * @parameter expression="${my.file}" default-value=
	 *            "${project.build.directory}/${project.build.finalName}.jar"
	 * @required
	 */
	private String jarfile;

	/**
	 * This populates the message to print.
	 * 
	 * @parameter required default-value=""
	 */
	private String deleteNodePaths;

	/**
	 * Workspace name.  This property may be set by the
	 * <code>crx.workspace</code>
	 * @parameter expression="${crx.workspace}"
	 * 
	 * @parameter required default-value="false"
	 */
	private String workspace;

	/**
	 * Login name.This property may be set by the
	 * <code>crx.login</code>
	 * @parameter expression="${crx.login}"
	 * @parameter required
	 */
	private String login;

	/**
	 * Password. This property may be set by the
	 * <code>crx.password</code>
	 * @parameter expression="${crx.password}"
	 * @parameter required
	 */
	private String password;

	/**
	 * Login name.This property may be set by the
	 * <code>crx.login</code>
	 * @parameter expression="${crx.path}" default-value="crx"
	 * @parameter required
	 */
	private String crxPath = "crx";
	
	/**
	 * Package path. The path for the package to be installed to.
	 * <code>package.path</code>
	 * @parameter expression="${package.path}" default-value=""
	 * @parameter required
	 */
	private String packagePath = "";

	/**
	 * inherited.
	 * 
	 * {@inheritDoc}
	 * 
	 * @see org.apache.maven.plugin.AbstractMojo#execute() {@inheritDoc}
	 */
	public final void execute() throws MojoExecutionException
	{
		// don't do anything, if this step is to be skipped
		if (skip)
		{
			getLog().info("Skipping crxpackage installation as instructed");
			return;
		}
		
		Cookie[] cookies = getCookies();
		if (cookies != null)
		{
			getSession(cookies);
			for (int i = 0; i < cookies.length; i++)
			{
				getLog().info(cookies[i].getName() + "=" + cookies[i].getValue());
			}
			if (enableBackup)
			{
				backUp(cookies);
			}
			if (!backupOnly)
			{
				if (deleteNodePaths != null && deleteNodePaths.startsWith("/"))
				{
					StringTokenizer paths = new StringTokenizer(deleteNodePaths, ";");
					while (paths.hasMoreTokens())
					{
						deleteNode(cookies, paths.nextToken());
					}
					saveAll(cookies);
				}

				// don't install anything, if this step is to be skipped
				if (skipInstall)
				{
					getLog().info("Skipping crxpackage installation as instructed");
					return;
				}
				else
				{
					uploadPackage(cookies);
					installPackage(cookies);
				}
				checkin(cookies);
			}
		}
	}
	
	private void checkin(Cookie[] cookies) throws MojoExecutionException
	{
		if (deleteNodePaths != null && deleteNodePaths.startsWith("/"))
		{
			HttpClient client = new HttpClient();
			client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
			client.getState().addCookies(cookies);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(
					CONNECTION_DEFAULT_TIMEOUT);
			
			StringTokenizer paths = new StringTokenizer(deleteNodePaths, ";");
			while (paths.hasMoreTokens())
			{
				String[] pathes = paths.nextToken().split(",");

				for (String path : pathes)
				{
					try
					{
						if (isVersionable(cookies, path, client))
						{
							getLog().info("Node at : " + path + " is mix:versionable.");
							checkin(cookies, path, client);
						}
					}
					catch (Exception e)
					{
						getLog().error("ERROR: " + e.getClass().getName() + " " + e.getMessage());
					}
				}
			}
			saveAll(cookies);
		}
	}
	
	/**
	 * Get the path to install the package to.
	 * @param file package file
	 * @return the path to install the package to.
	 */
	private String getPackagePath(File file)
	{
		return StringUtils.isNotEmpty(this.packagePath) ?
				this.packagePath + "/" + file.getName() : "/etc/packages/" + file.getName();
	}

	/**
	 * @return the cookies return from this request on the login page.
	 * @throws MojoExecutionException
	 *             if any error occurs during this process.
	 */
	private Cookie[] getCookies() throws MojoExecutionException
	{
		Cookie[] cookie = null;
		GetMethod loginGet = new GetMethod(crxPath + "/login.jsp");
		loginGet.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		try
		{
			getLog().info("login to " + loginGet.getURI());
			HttpClient client = new HttpClient();
			client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
			int status = client.executeMethod(loginGet);
			// log the status
			getLog().info("Response status: " + status + ", statusText: " + HttpStatus.getStatusText(status) + "\r\n");
			if (status == HttpStatus.SC_OK)
			{
				getLog().info("Login page accessed");
				cookie = client.getState().getCookies();
			}
			else
			{
				logResponseDetails(loginGet);
				throw new MojoExecutionException("Login failed, response=" + HttpStatus.getStatusText(status));
			}
		}
		catch (Exception ex)
		{
			getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
			throw new MojoExecutionException(ex.getMessage());
		}
		finally
		{
			loginGet.releaseConnection();
		}
		return cookie;
	}

	/**
	 * @param cookies
	 *            get a session using the same existing previously requested
	 *            response cookies.
	 * @throws MojoExecutionException
	 *             if any error occurred during this process.
	 */
	@SuppressWarnings("deprecation")
	private void getSession(final Cookie[] cookies) throws MojoExecutionException
	{
		PostMethod loginPost = new PostMethod(crxPath + "/login.jsp");
		loginPost.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		try
		{
			getLog().info("login to " + loginPost.getPath());
			loginPost.setParameter("Workspace", workspace);
			loginPost.setParameter("UserId", login);
			loginPost.setParameter("Password", password);
			HttpClient client = new HttpClient();
			client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
			client.getState().addCookies(cookies);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
			int status = client.executeMethod(loginPost);
			// log the status
			getLog().info("Response status: " + status + ", statusText: " + HttpStatus.getStatusText(status) + "\r\n");
			if (status == HttpStatus.SC_MOVED_TEMPORARILY)
			{
				getLog().info("Login successful");
			}
			else
			{
				logResponseDetails(loginPost);
				throw new MojoExecutionException("Login failed, response=" + HttpStatus.getStatusText(status));
			}
		}
		catch (Exception ex)
		{
			getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
			throw new MojoExecutionException(ex.getMessage());
		}
		finally
		{
			loginPost.releaseConnection();
		}
	}

	/**
	 * @param cookies
	 *            the previous requests cookies to keep in the same session.
	 * @throws MojoExecutionException
	 *             if any error occurs during this process.
	 */
	@SuppressWarnings("deprecation")
	private void uploadPackage(final Cookie[] cookies) throws MojoExecutionException
	{
		PostMethod filePost = new PostMethod(crxPath + "/packmgr/list.jsp");
		filePost.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		try
		{
			getLog().info("Uploading " + jarfile + " to " + filePost.getPath());
			File jarFile = new File(jarfile);
			Part[] parts = { new FilePart("file", jarFile) };
			filePost.setRequestEntity(new MultipartRequestEntity(parts, filePost.getParams()));
			HttpClient client = new HttpClient();
			client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
			client.getState().addCookies(cookies);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
			int status = client.executeMethod(filePost);
			// log the status
			getLog().info("Response status: " + status + ", statusText: " + HttpStatus.getStatusText(status) + "\r\n");
			if (status == HttpStatus.SC_MOVED_TEMPORARILY)
			{
				getLog().info("Upload complete");
			}
			else
			{
				logResponseDetails(filePost);
				throw new MojoExecutionException("Package upload failed, response=" + HttpStatus.getStatusText(status));
			}
		}
		catch (Exception ex)
		{
			getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
			throw new MojoExecutionException(ex.getMessage());
		}
		finally
		{
			filePost.releaseConnection();
		}
	}

	/**
	 * Logs response details to debug and logs error message as error if found
	 * @param filePost
	 * @throws IOException
	 */
	private void logResponseDetails(HttpMethodBase filePost) throws IOException
	{
		InputStream stream = filePost.getResponseBodyAsStream();
		if (stream == null)
		{
			throw new IOException("Null response stream");
		}

		String responseBody = IOUtils.toString(stream);
		getLog().debug("Response body: " + responseBody);

		String errorPattern = "(?<=<span class=\"error_line\">)(.+)(?=</span>)";
		Pattern regex = Pattern.compile(errorPattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
		Matcher matcher = regex.matcher(responseBody);

		StringBuilder errorMessage = new StringBuilder();

		while (matcher.find())
		{
			errorMessage.append(matcher.group() + " ");
		}

		if (!StringUtils.isEmpty(errorMessage.toString()))
		{
			getLog().error(errorMessage.toString());
		}
	}

	/**
	 * @param cookies
	 *            the previous request response existing cookies to keep the
	 *            session information.
	 * @throws MojoExecutionException
	 *             in case of any errors during this process.
	 */
	@SuppressWarnings("deprecation")
	private void installPackage(final Cookie[] cookies) throws MojoExecutionException
	{
		File file = new File(jarfile);
		String url = crxPath + "/packmgr/unpack.jsp?Path=" + getPackagePath(file)
				+ (aclIgnore ? "" : "&acHandling=overwrite");

		GetMethod loginPost = new GetMethod(url);
		loginPost.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		try
		{
			getLog().info("installing: " + url);
			HttpClient client = new HttpClient();
			client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
			client.getState().addCookies(cookies);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
			int status = client.executeMethod(loginPost);
			// log the status
			getLog().info("Response status: " + status + ", statusText: " + HttpStatus.getStatusText(status) + "\r\n");
			// if it's ok, proceed
			if (status == HttpStatus.SC_OK)
			{
				InputStream response = loginPost.getResponseBodyAsStream();
				if (response != null)
				{
					String responseBody = IOUtils.toString(response);
					if (responseBody.contains("Package installed in"))
					{
						getLog().info("Install successful");
					}
					else
					{
						logResponseDetails(loginPost);
						throw new MojoExecutionException("Error installing package on crx");
					}
				}
				else
				{
					throw new MojoExecutionException("Null response when installing package on crx");
				}
			}
			else
			{
				logResponseDetails(loginPost);
				throw new MojoExecutionException("Installation failed");
			}
		}
		catch (Exception ex)
		{
			getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
			throw new MojoExecutionException(ex.getMessage());
		}
		finally
		{
			loginPost.releaseConnection();
		}
	}

	/**
	 * @param cookies
	 *            the previous request response existing cookies to keep the
	 *            session information.
	 * @param path
	 *            the node path to delete.
	 * @throws MojoExecutionException
	 *             in case of any errors during this process.
	 */
	@SuppressWarnings("deprecation")
	private void deleteNode(final Cookie[] cookies, final String pathInput) throws MojoExecutionException
	{
		String[] pathes = pathInput.split(",");

		for (String path : pathes)
		{
			GetMethod removeCall = new GetMethod(crxPath + "/browser/delete_recursive.jsp?Path=" + path
					+ "&action=delete");
			removeCall.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
			try
			{
				getLog().info("removing " + path);
				getLog().info(crxPath + "/browser/delete_recursive.jsp?Path=" + path + "&action=delete");

				HttpClient client = new HttpClient();
				client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
				client.getState().addCookies(cookies);
				client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
				removeCall.setFollowRedirects(false);

				if (isVersionable(cookies, path, client))
				{
					getLog().info("Node at : " + path + " is mix:versionable.");
					checkout(cookies, path, client);
				}

				else
				{
					
					getLog().info("removing " + path);
					getLog().info(
							crxPath + "/browser/delete_recursive.jsp?Path="
									+ path + "&action=delete");

					int status = client.executeMethod(removeCall);

					if (status == HttpStatus.SC_OK)
					{
						getLog().info("Node deleted");
						// log the status
						getLog().info(
								"Response status: " + status
										+ ", statusText: "
										+ HttpStatus.getStatusText(status)
										+ "\r\n");
					}
					else
					{
						logResponseDetails(removeCall);
						throw new MojoExecutionException("Removing node "
								+ path + " failed, response="
								+ HttpStatus.getStatusText(status));
					}
					getLog().info(
							"Response status: " + status + ", statusText: "
									+ HttpStatus.getStatusText(status)
									+ "\r\n");
				}
			}
			catch (Exception ex)
			{
				getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
				throw new MojoExecutionException(ex.getMessage());
			}
			finally
			{
				removeCall.releaseConnection();
			}
		}

	}

	/**
	 * @param cookies
	 *            the previous request response existing cookies to keep the
	 *            session information.
	 * @throws MojoExecutionException
	 *             in case of any errors during this process.
	 */
	@SuppressWarnings("deprecation")
	private void saveAll(final Cookie[] cookies) throws MojoExecutionException
	{
		GetMethod savePost = new GetMethod(crxPath + "/browser/content.jsp?Path=/&action_ops=saveAll");
		savePost.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		try
		{
			getLog().info("save all changes");
			HttpClient client = new HttpClient();
			client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
			client.getState().addCookies(cookies);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
			int status = client.executeMethod(savePost);
			// log the status
			getLog().info("Response status: " + status + ", statusText: " + HttpStatus.getStatusText(status) + "\r\n");
			if (status == HttpStatus.SC_OK)
			{
				getLog().info("All changes saved");
			}
			else
			{
				logResponseDetails(savePost);
				throw new MojoExecutionException("save all changes failed, response="
						+ HttpStatus.getStatusText(status));
			}
		}
		catch (Exception ex)
		{
			getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
			throw new MojoExecutionException(ex.getMessage());
		}
		finally
		{
			savePost.releaseConnection();
		}
	}

	private boolean isVersionable(final Cookie[] cookies, String path, HttpClient client) throws HttpException,
			IOException, MojoExecutionException
	{
		GetMethod definitionCall = new GetMethod(crxPath + "/browser/definition.jsp?Path=" + path);
		definitionCall.setFollowRedirects(false);

		getLog().info("Getting definitions for node : " + path);
		getLog().info(crxPath + "/browser/definition.jsp?Path=" + path);

		int status = client.executeMethod(definitionCall);

		if (status == HttpStatus.SC_OK)
		{
			getLog().info("Successfully retrieved node definition.");
		}
		else
		{
			logResponseDetails(definitionCall);
			throw new MojoExecutionException("Getting definitions for node " + path + " failed, response="
					+ HttpStatus.getStatusText(status));
		}

		String response = definitionCall.getResponseBodyAsString().toLowerCase();

		return StringUtils.contains(response, "mix:versionable");
	}

	private void checkout(final Cookie[] cookies, String path, HttpClient client) throws HttpException, IOException,
			MojoExecutionException
	{
		getLog().info("Checking out " + path);
		getLog().info(crxPath + "/browser/content.jsp?Path=" + path + "&action_ops=checkout");

		PostMethod checkoutCall = new PostMethod(crxPath + "/browser/content.jsp");
		checkoutCall.setFollowRedirects(false);

		checkoutCall.addParameter("Path", path);
		checkoutCall.addParameter("action_ops", "checkout");

		int status = client.executeMethod(checkoutCall);

		if (status == HttpStatus.SC_OK)
		{
			getLog().info("Successfully checked out.\r\n");
		}
		else
		{
			logResponseDetails(checkoutCall);
			throw new MojoExecutionException("Removing node " + path + " failed, response="
					+ HttpStatus.getStatusText(status));
		}
	}

	private void checkin(final Cookie[] cookies, String path, HttpClient client)
		throws HttpException, IOException, MojoExecutionException
	{
		getLog().info("Checking in " + path);
		getLog().info(
				crxPath + "/browser/content.jsp?Path=" + path + "&action_ops=checkin");
		
		PostMethod checkinCall = new PostMethod(crxPath + "/browser/content.jsp");
		checkinCall.setFollowRedirects(false);
		
		checkinCall.addParameter("Path", path);
		checkinCall.addParameter("action_ops", "checkin");
		
		int status = client.executeMethod(checkinCall);
		
		if (status == HttpStatus.SC_OK)
		{
			getLog().info("Successfully checked in.\r\n");
		}
		else
		{
			logResponseDetails(checkinCall);
			throw new MojoExecutionException("Removing node " + path + " failed, response="
					+ HttpStatus.getStatusText(status));
		}
	}
	
	@SuppressWarnings("deprecation")
	private void backUp(final Cookie[] cookies) throws MojoExecutionException
	{
		checkBackupFolder();
		File file = new File(jarfile);
		GetMethod backupPost = new GetMethod(crxPath + "/packmgr/service.jsp?cmd=get&_charset_=utf8&name="
				+ FilenameUtils.getBaseName(file.getName()));
		backupPost.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		try
		{
			getLog().info("backing up /etc/packages/" + file.getName());
			HttpClient client = new HttpClient();
			client.getState().setCookiePolicy(CookiePolicy.COMPATIBILITY);
			client.getState().addCookies(cookies);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(CONNECTION_DEFAULT_TIMEOUT);
			int status = client.executeMethod(backupPost);
			// log the status
			getLog().info("Response status: " + status + ", statusText: " + HttpStatus.getStatusText(status) + "\r\n");

			String backUpFileName = formatbackUpFileName(file);

			File backupFile = new File(backUpFileName);

			if (status == HttpStatus.SC_OK)
			{
				copyStreamToFile(backupPost.getResponseBodyAsStream(), backupFile);
				getLog().info("Back-up succesfull. The backup is " + backupFile.getAbsolutePath());
			}
			else
			{
				logResponseDetails(backupPost);
				throw new MojoExecutionException("Back-up failed, response=" + HttpStatus.getStatusText(status));
			}
		}
		catch (Exception ex)
		{
			getLog().error("ERROR: " + ex.getClass().getName() + " " + ex.getMessage());
			throw new MojoExecutionException(ex.getMessage());
		}
		finally
		{
			backupPost.releaseConnection();
		}
	}

	/**
	 * Creates the name for the backup file.
	 * @param file file
	 * @return formatted file name
	 */
	private String formatbackUpFileName(File file)
	{
		String baseName = FilenameUtils.getBaseName(file.getName());
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
		String timeStamp = sdf.format(cal.getTime());
		String backUpFileName = backupFolder.getAbsolutePath() + "/" + baseName + "_" + timeStamp + ".zip";
		return backUpFileName;
	}

	/**
	 * Performs checks on backup folder.
	 * If backup folder does not exist, it attends to create it.
	 * Then the folder is checked for write permission.
	 * @throws MojoExecutionException exception
	 */
	private void checkBackupFolder() throws MojoExecutionException
	{
		if (!backupFolder.exists())
		{
			try
			{
				FileUtils.forceMkdir(backupFolder);
			}
			catch (IOException e)
			{
				getLog().error("Back-up failed. " + backupFolder.getAbsolutePath() + " cannot be created.");
				throw new MojoExecutionException("Error backing up package " + jarfile, e);
			}
		}
		if (!backupFolder.canWrite())
		{
			getLog().error("Back-up failed. " + backupFolder.getAbsolutePath() + " cannot be written.");
			throw new MojoExecutionException("Error backing up package " + jarfile);
		}

		if (!backupFolder.isDirectory())
		{
			getLog().error("Back-up failed. " + backupFolder.getAbsolutePath() + " is not a directory.");
			throw new MojoExecutionException("Error backing up package " + jarfile);
		}
	}

	/**
	 * Copies stream to a file.
	 * @param input the input stream
	 * @param destination  File to write to
	 * @throws IOException exception
	 */
	public static void copyStreamToFile(InputStream input, File destination) throws IOException
	{

		try
		{
			FileOutputStream output = FileUtils.openOutputStream(destination);
			try
			{
				IOUtils.copy(input, output);
			}
			finally
			{
				IOUtils.closeQuietly(output);
			}
		}
		finally
		{
			IOUtils.closeQuietly(input);
		}
	}

}

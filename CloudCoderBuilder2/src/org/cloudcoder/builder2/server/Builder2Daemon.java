// CloudCoder - a web-based pedagogical programming environment
// Copyright (C) 2011-2012, Jaime Spacco <jspacco@knox.edu>
// Copyright (C) 2011-2012, David H. Hovemeyer <dhovemey@ycp.edu>
// Copyright (C) 2013, York College of Pennsylvania
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.cloudcoder.builder2.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.cloudcoder.builder2.csandbox.EasySandboxSharedLibrary;
import org.cloudcoder.builder2.extlib.ExternalLibraryCache;
import org.cloudcoder.builder2.javasandbox.JVMKillableTaskManager;
import org.cloudcoder.builder2.pythonfunction.PythonKillableTaskManager;
import org.cloudcoder.daemon.IDaemon;
import org.cloudcoder.daemon.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link IDaemon} to start, control, and shutdown
 * a Builder instance.
 * 
 * @author David Hovemeyer
 */
public class Builder2Daemon implements IDaemon {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private List<BuilderAndThread> builderAndThreadList;

	private static class BuilderAndThread {
		final Builder2Server builder;
		final Thread thread;
		public BuilderAndThread(Builder2Server builder, Thread thread) {
			this.builder = builder;
			this.thread = thread;
		}
	}
	
	static class Options {
		private Properties config;

		public Options(Properties config) {
			this.config = config;
		}
		
		// The default property values are appropriate for running interactively for development.

		public String getAppHost() {
			return config.getProperty("cloudcoder.submitsvc.oop.host", "localhost");
		}

		public int getAppPort() {
			return Integer.parseInt(config.getProperty("cloudcoder.submitsvc.oop.port", "47374"));
		}

		public int getNumThreads() {
			return Integer.parseInt(config.getProperty("cloudcoder.submitsvc.oop.numThreads", "2"));
		}
		
		public String getKeystoreFilename() {
			return config.getProperty("cloudcoder.submitsvc.ssl.keystore", "defaultkeystore.jks");
		}
		
		public String getKeystorePassword() {
			return config.getProperty("cloudcoder.submitsvc.ssl.keystore.password", "changeit");
		}
	}

	/* (non-Javadoc)
	 * @see org.cloudcoder.daemon.IDaemon#start(java.lang.String)
	 */
	@Override
	public void start(String instanceName) {
		// If embedded configuration properties exist, read them
		Properties config;
		try {
			String configPropPath = "cloudcoder.properties";
			ClassLoader clsLoader = this.getClass().getClassLoader();
			config = Util.loadPropertiesFromResource(clsLoader, configPropPath);
		} catch (IllegalStateException e) {
			logger.warn("Could not load cloudcoder.properties, using default config properties");
			config = new Properties();
			
			// Enable EasySandbox by default
			config.setProperty("cloudcoder.submitsvc.oop.easysandbox.enable", "true");
			config.setProperty("cloudcoder.submitsvc.oop.easysandbox.heapsize", "8388608");
		}
		
		Options options = new Options(config);
		
		// Create the WebappSocketFactory which the builder tasks can use to create
		// connections to the webapp.
		WebappSocketFactory webappSocketFactory;
		try {
			webappSocketFactory = new WebappSocketFactory(
					options.getAppHost(),
					options.getAppPort(),
					options.getKeystoreFilename(),
					options.getKeystorePassword());
		} catch (Exception e) {
			logger.error("Could not create WebappSocketFactory", e);
			throw new IllegalStateException("Could not create WebappSocketFactory", e);
		}
		
		// Install KillableTaskManager's security manager
		JVMKillableTaskManager.installSecurityManager();
		PythonKillableTaskManager.installSecurityManager();
		
		logger.info("Builder starting");
		logger.info("appHost={}", options.getAppHost());
		logger.info("appPort={}", options.getAppPort());
		logger.info("numThreads={}", options.getNumThreads());
		
		// Start Builder threads
		this.builderAndThreadList = new ArrayList<BuilderAndThread>();
		for (int i = 0; i < options.getNumThreads(); i++) {
			Builder2Server builder_ = new Builder2Server(webappSocketFactory, config);
			Thread thread_ = new Thread(builder_);
		
			BuilderAndThread builderAndThread = new BuilderAndThread(builder_, thread_);
			builderAndThreadList.add(builderAndThread);
			
			builderAndThread.thread.start();
		}
	}

	/* (non-Javadoc)
	 * @see org.cloudcoder.daemon.IDaemon#handleCommand(java.lang.String)
	 */
	@Override
	public void handleCommand(String command) {
		// Right now the Builder has no runtime configuration commands
		logger.warn("Builder received unknown command " + command);
	}

	/* (non-Javadoc)
	 * @see org.cloudcoder.daemon.IDaemon#shutdown()
	 */
	@Override
	public void shutdown() {
		// Shut down all Builder threads
		for (BuilderAndThread builderAndThread : builderAndThreadList) {
			try {
				builderAndThread.builder.shutdown();
				builderAndThread.thread.join();
				logger.info("Finished");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Ensure that if the EasySandbox shared library was built,
		// that its directory is deleted before the daemon exits.
		EasySandboxSharedLibrary.getInstance().cleanup();
		
		// Delete directories/files used by the ExternalLibraryCache
		ExternalLibraryCache.getInstance().cleanup();
	}

}

## Changelog 

### 3.4.10 (pending)

* update to sushi 3.8.10 to work with Jdk 1.8.0_144 (thanks to Marcus T)
  * Stool itself did not build
  * `stool start` failed to created the service-wrapper.sh
  

### 3.4.9 (2017-08-01)

* added `fault` extension to start stages with fault workspace
* changed default Tomcat version from 8.5.8 to 8.5.16; switched the default download location from http to https
* changed default Service Wrapper version from 3.5.30 to 3.5.32; adjusted default download location from
  http://wrapper.tanukisoftware.com/download/$v to https://wrapper.tanukisoftware.com/download/$v?mode=download
* added `heap` status field indicating percentage of max heap actually used (for Stefan H)
* added user defaults `svn.user` and `svn.password` to define defaults for `-svnuser` and `-svnpassword` (for Stefan H)
* improved quota error message (for Andreas K)
* fixed `stool create gav:...` for multiple artifacts - refresh directory was created more once (for Radek S)
* fixed Tomcat configuration for non-empty context path - the initial slash was missing, which yields a Tomcat warning
  (thanks to Max B)
* fixed stale stage wiper to *not* remove inaccessible stages;
  the old behaviour was very confusing: if a user created a stage in his home (and maybe started it), the stage was 
  considered stale and automatically wiped (without no way to stop it) if a different user invoked Stool; 
  the new behavior issues an 'inaccessible' warning instead of removing it
* former stage fixes (a former stage is not a stage, it's just a directory with a .backstage subdirectory which is not
  references from stool/backstages):  
  * fixed stage indicate to *not* flag former stages as stages
  * fixed 'stool list' and 'stool status' if the current directory is a former stage 


### 3.4.8 (2017-01-23)

* renamed status fields:
  * `maintainer` becomes `last-modified-by`
  * `maintained` becomes `last-modified-at`
* changed default value for `notify` from `@maintainer @creator` to `@creator`
* fixed url defaults, they never worked (thanks to Max)
* ldap tweaks (thanks to Stefan H)
  * dashboard sso is now configured with the `sso` property in `$STOOL_HOME/system/dashboard.properties`
  * ldap unit (for ldap lookup and dashboard sso) is now configurable via ldap.sso 
    (ldap.sso will be renamed to ldap.unit in stool 3.5; it's not renamed now to avoid the migration efford)
* dumped `system-start` and `system-stop`, use `start -all`  and `stop -all` instead
* adjust autoconf url for cp: replaced /xml/config by (/internal-login)
* added UpgradeProtocol configuration for Tomcat 8.5 and 9


### 3.4.7 (2016-12-27)

* fixed build command locking: lock mode none erroneously created a shared lock, which caused a single build command to block any start command
  lock (many thanks to Christina for detecting this!)
* fixed NPE in `stool status` for stale tomcat.pid file (for Simon)


### 3.4.6 (2016-12-08) 

* added `start -fitnesse` and `restart -fitnesse` options to start the fitnesse wikis instead of the applications; 
  also added a `Start Fitness` action to the dashboard; running fitnesse wikis is indicated by the new status field `fitnesse`. 
* disabled the fitnesse extension - setting the `fitnesse` property to true will abort `stool start` with an error
* fixed port garbage collection - unused ports are properly freed now
* `stool validate`: fixed duplicate lines in console output
* changed default `tomcat.version` from 8.5.6 to 8.5.8 (which fixes "Unable to add the resource at *somePath* to the cache" )
* changed default `service.version` from 3.5.29 to 3.5.30
* locking tweaks (for Stefan M. and Tesa): 
  * `cleanup` no longer acquires directory locks
  * `list` and `status` no acquires fetches directory locks
  * `move` acquires exclusive directory locks now
  * `remove` acquires exclusive backstage and directory locks now
* $STOOL_HOME is configurable now, default is ~/.stool
* Debian packages reworked:
  * Main package:
    * No longer contains a home directory. Instead, the default is that every user creates his own home.
    * Removed the Debian service. Because there's no longer a unique home to start stages from.
  * Dashboard package: dumped. Because there's no longer a unique home to install it to.
* Development:
  * enable service wrapper debug output if stool was invoked with `-v`
  * simplified build: no longer depends on a custom version of jdeb
  * speedup dashboard build
* improved error message if a backstage directory has no link in `backstages` (for Max).


### 3.4.5 (2016-11-21)

* CAUTION: stop all stages before updating from 3.4.x. (Because the stop mechanism has changed, 3.4.5 cannot stop stages started from older versions.)

* `stool status`: 
  * added new fields:
    * `created` indicates when a stage was created
    * `maintainer` indicates the person that last changed a stage
    * `maintained` is the timestamp of this change
  * fixed exception if service wrapper has no child process

* improved `history` command:
  * speed-up
  * print latest command first
  * simplified details handling: 
    * it's just a global `-details` switch now
    * added `history.details` defaults settings
  * properly log stacktraces, also fixed escaping
  * include create and import commands for the stage
  
* Stool user (for Philipp)
  * Stool now distinguishes the Stool user (used for logging and emails) from the OS user 
    (owner of files and processes, someone with an account on the local machine)
  * configured via environment variable `STOOL_USER`. It not defined, Stool uses the Java system property `user.name`

* chown-less operation:
  * dumped `chown` command since it's no longer needed; instead, admins now have to take care that sharing the files does not cause permission problems
  * removed -autoChown and -autoRechown options
  * removed `owner` status field; changed the dashboard to show the `maintainer` instead

* dumped all sudo rules and `$STOOL_HOME/bin` scripts
  * =chowntree.sd= (no longer needed, because chown is gone; see chown-less operation above). This fixes a critical security problem (thanks to Bernd!)
  * invoke service-wrapper without sudo
    * instead, it's now started as the current OS user
    * and it's stopped with via anchor file; every user with wx permissions on $backstage/run and $backstage/run/tomcat.anchor can stop a stage 
  * dashboard now invokes Stool commands without sudo, it sets the Stool user to the authenticated user (or, it not authenticated, the Stool user of the dashboard itself)

* stage indicator color is gone: red no longer makes sense, because there's no chown you have to use; and blue was already impossible since  
  the backstage directory moved into the stage directory in Stool 3.4.0

* improved `import` command: re-use backstage directory if it already exists

* improved build command (for Christina)
  * added command parameters to be executed instead of the configured `build` property 
  * added a `-here` option to execute in the current directory

* improved expire property (for Stefan)
  * you can now specify a number instead of a date; it will be translated into the specified number of days from today
  * you can specify the number in your defaults
  * build-in default is now always `never`, it no longer depends on the shared mode; use defaults to configure direfferent values

* fixed `noSuchDirectory` in catalina base, there's now an empty `webapps` directory instead (for Felix).

* bumped lock timeout from 10 to 30 seconds (for Falk).

* Debian packages
  * no longer create a group or a user - both are assumed to already exist now.
  * no longer create a cron jobs - that's up to the user now (because it easy to setup and every body has slightly different needs)


### 3.4.4 (2016-10-17)

* updated default Tomcat version from 8.5.3 to 8.5.6
* fixed bash completion for systems where `ls` is an alias
* removed support for IT-CA certificates
* logstash improvements: `logstash.output` is unused now, the launcher script (configured in `logstash.link` 
  is now expected to generate the configuration as well


### 3.4.3 (2016-09-22)

* `stool validate` now sends a notification if the certificate expires in less than 10 days.
* logstash improvements
  * one logstash process per stage
  * logstash.link now configures a bash script to actually launch the logstash process 
  * logstash.output now specifies a list of files to append for the *input* config generated by stool
* fixed `stool create` gav in shared mode (thanks to Maximilian)
* fixed `stool refresh` on artifact stages
* fixed performance problem in `stool list` (thanks to Gelli)
* fixed Dashboard candel butten - by removing it (thanks to Cosmin)


### 3.4.2 (2016-08-11)

* Dumped predefined STOOL_OPTS from /etc/profile.de/stool
* Global `certificates` property now also accepts a script file name to generate the Java keystore used by Tomcat.
* Added setup `-batch` option
* Converted documentation from docbook to markdown; merged site module into main.


### 3.4.1 (2016-08-02)

* auto options fix: restart running stages if it needs chowning and both -autorestart and -autorechown are specified
* changed default quota from 40000 to 10000
* fixed NPE in `history` command
* fixed quota computation: skip negative values
* `list` now prints the reserved quota (instead of the available space on the home partition)
* fixed `create` and the `diskMin` property to check available space on the target partion, not the partition for Stool home.
* fixed duplicate line in email notifications
* changed logging to create log files with the date appended; gzip them after a day, remove them after 90 days.
* added `creator` status field and `@creator` email alias
* stage indicator tweaks
  * prefix functions with __ to keep namespace and bash completion clean
  * fixed control sequence escaping in stage indicator
  * fixed color to always check the stage directory itself
* implementation changes:
  * speedup `stool list`
  * update Java Mail 1.5.0b1 to 1.5.5

  
### 3.4.0 (2016-07-28)

* auto select:
  * the selected stage is now determined by the current directory
  * to build a stage with the proper environment, you have to use `stool build` now. 
    Stool no longer adjust the environment for the current stage.

* certificate handling
  * generate self-signed certificate if no `certificates` url is specified
  * generate puki certificates for other urls
  
* added support for git urls. To distinguish between svn- and git urls, source stage urls are now prefixed with `svn:` or `git:`.

* system stages are now placed in $HOME/system

* per-user defauls: Stool now checks for a user's `~/.stool.defaults` file. If it exists, Stool loads it as a properties 
  file and uses it as default values for options. For example, a property `refresh.build=true` causes `stool refresh` to build a stage
  without explicitly specify `-build`. To override this default, use `stool refresh -build=false`. 
  For a list of available properties, see the "User defaults" section of the documentation.

* Added `select -fuzzy` option a stage if the specifed name is not found but there's ony one suggestion.

* Logstash extension

* `status` command
  * added `cpu`, `mem`, `selected` and `buildtime` fields
  * can also access properties now (actually, name is a property)
  * fixed status field `tomcat` to contain the tomcat pid, not the service wrapper pid; added a new `service` field to contain the 
    service wrapper pid.

* `list` command has the same configurable fields and properties as the `status` command now. Defaults no longer contain the 
  selected stage; the stage directory has been added to the defaults.

* `start` command now deletes $TOMCAT/temp and creates a new empty directory

* The stage name is a property now.
  * `stool rename foo` is gone, use `stool config name=foo` instead. 
  * `stool create -name foo url` is gone, use `stool create url name=foo` instead.

* Global configuration:
  * renamed `contactAdmin` to `admin`.
  * removed `prompt`, it's no longer needed because is a plain executable now. 
  * Pommes is no longer hard-wired, there's a global `search` property to configure arbitrary search tools.

* Stage configuration:
  * default base heap is 350 now
  * added `quota` to limit the disk spaced occupied by the stage.
  * added `notify` to configure email notifications.
  * dumped `sslUrl` 
  * renamed `suffixes` to `url`. Changed the syntax so you can specify protocols, prefixes, suffixes and a context
  * renamed `until` to `expire` (and the value `reserved` to `never`)
  * removed `tomcat.perm` because it's ignored by Java 8.
  * changed default tomcat version from 8.0.26 to 8.5.3
  * changed default service wrapper version from 3.5.26 to 3.5.29
  * changed default diskMin to 1000


* simplified directory structure:
  * backstage directories moved from `$LIB/backstages/stagename` to `stagedir/.backstage`.
  * dumped `$BACKSTAGE/shared`, $BACKSTAGE now directly contains all subdirectories
  * renamed `$BACKSTAGE/conf` to `$BACKSTAGE/service`

* changed system-start and system-stop to always use fail mode after
* start and stop for multiple stages is executed in parallel now. 

* properties for running stages
  * replaced `stool.bin` by `stool.cp`, it points to Stool's application file
  * renamed `stool.backstage` by `stool.idlink`, it points to the link in $LIB/backstages

* improved setup
  * `stool` is now a normal executable, not a shell function. Shell code went to $LIB/shell.rc, and it's only needed for interactive shells. 
  * arbitrary location for `stool` binary
  * $LIB is either `~/.stool` or `/usr/share/stool`
  * improved default configuration
  * atomic upgrade: creates a backup of the lib directory before upgrade; this is restored if the upgrade fails

* fixes
  * Fixed machine reboot problems with stale pid files by making the service wrapper timeout shorter than the systemctl timeout.
  * Fixed broken locks file for command lines containing \n
  * Fixed stage stop for applications with fitnesse extension if war files have been remove
  * Fixed `system-import` command.
  
* cleanup
  * dashboard debian package with https only
  * dumped `MACHINE` and `STAGE_HOST` environment variables
  * dumped `$LIB/run/users` directory
  * replaced curl- (stop fitnesse) and wget (downloads) execs by sushi http.
  * fixed `tomcat.service` to actually work for versions other than 3.5.26.

* Implementation changes
  * simplified multi-module build; dumped the `$STOOL_SOURCE` variable.
  * dependency updates:
    * Sushi 2.8.18 to 3.1.1 and inline 1.1.0
    * Maven Embedded 3.11.1 to 3.12.1
    * gson 2.2.4 to 2.7
  * dumped dependencies to slf4j-api and logback

* fixed logging of credential arguments


### 3.3.5 (2016-03-16)

* Fixed dns validation to use proper hostname and port.


### 3.3.4 (2016-03-15)

* Merged system-validate into the validate command.
* Improved validate: send an email if your stage was removed; send on email per user, even for multiple broken stages; a single
  `-repair` option replaces the previous `-stop` and `-repair-locks` options; check that the configured hostname is this machine.
* Fixed stop problem when a stage is in debug mode: set timeout to 1h (instead of infinite).
* Changed initial java.home to optionally remove the tailing /jre. This fixes a problem on Mac OS when invoking Java command line tools like jar.
* Print an error message the user tries to start pustfix editor (to prepare to dump it)
* Print uptime in days if it's more than 48 hours.


### 3.3.3 (2016-03-09)

* Added an uptime field to stool status.
* Fixed synchronization issue in LockManager shutdown hook.
* Validate now detects stale locks and optionally repairs them.
* Allow unauthenticated http://dashboard/stages requests.
* Add java8-sdk as an alternative package dependency (to fix install problem with OpenJdk from PPA).
* Service wrapper: don't restart on OutOfMemory, the user should have a look instead.
* Improved stale backstage wiper: invoke stop with proper user (and improved error message); wipe backstages without anchor file.


### 3.3.2 (2016-01-18)

* Fixed "devel" version number in exception mails from dashboard.
* stool create: Fixed "Artifact x:y:z not found" error message to include the underlying Maven problem (in particular "Unauthorized").
* Fixed missing date in history command.
* Fixed lost exception when probing svn checkouts.


### 3.3.1 (2015-12-21)

* Fixed missing stool function in non-login shells.
* Fixed missing vhosts in global config.json.
* Fixed run/users/root with empty prompt - 'service stool &lt;cmd>' no longer creates this file.
* Fixed group permissions for backstage/shared/ssl/tomcat.p12.
* Fixed NPE for predicate with unknown property or field; issue proper error message instead.
* Fixed exception emails.
* 'stool start' now reports an error if the hostname name cannot be resolved. And pinging the application has a 5 minutes timeout now.
* Changed the port command to separate application and port by '=' (indead of ':').
* Fixed Stool 3.2.x upgrade code to properly migrate ports of workspaces, special ports, and suffixes in global defaults.
* Generalized suffixes: if a suffix contains [], the resulting application url is the suffix with [] substituted by hostname:port.
* Fixed duplicate -autorechown/-autorestart options in dashboard refresh.
* Moved stage property description from the config command to the documentation (man stool-config).


### 3.3.0 (2015-11-30)

* Renamed overview to dashboard.
* Added '-autostop', '-autorestart', '-autochown' and '-autorechown' options for all stage commands.
* 'stool refresh': Removed implicit re-chowning and re-starting. If you need this functionality, invoke 'refresh' with the new auto
  options for stage commands: use 'stool refresh -autorechown -autorestart' to get the old behavior.
* 'stool remove': Removed implicit stopping. If you need this functionality, invoke 'stool remove -autostop'.
* Adjusted the 'chown' command for the new '-autorestart' options: 'chown' now reports an error, if the stage is up.
  Use  '-autorestart' to get the old behavior. The old '-stop' option is gone.
* Removed the 'build -restart' option, use '-autorestart' instead.
* Updated default tomcat version from 7.0.57 to 8.0.26.
* Fixed permission problem on Fitnesse log files; all output is written to backstage/shared/tomcat/logs/fitnesse.log now.
* Retry ldap query if an CommunicationException occured.
* Fitnesse extension: Fixed NPE during stage stop when fitnesse is enabled while tomcat is running.
* Fixed tomcat.perm configuration.
* @proxyOpts@ is a normal macro now, and it is no longer computed from the stage owner's environment. If you need proxy configuration,
  you defined it in the global configuration and adjust your stage defaults to use it for all stages.
* Removed Stool update-checks, the packaging system is responsible for this now.
* 'stool config' now supports a "{}" place holder to refer to the previous value. You can use this e.g. to append to a property:
  'stool config "tomcat.opts={} -Dfoo=bar".
* Fixed exception if history argument is not a number. Issue proper error message now.
* You can create artifact stages from either gav: or file: urls now. Example: 'stool create file:///path/to/my.war'.
  You can specify multiple comma-separates urls (instead of the previous multiple coordinates within one gav url).
* Improved setup: Debian package is configurable with debconf now, and it's separated into a stool and a stool-dashboard package now.
* Changed the 'suffix' stage property to 'suffixes', it holds a list now.
* Improved locking (i.e. less waiting for lock messages): replace the old global- and stage-semaphores by one global read/write lock,
  and per-stage a backstage read/write lock and a directory read/write lock.
* 'stool restart': remove 5 seconds sleep.
* Simplified cwd handling: only the following commands change the current directory: create, remove, select and cd.
* Removed allowLinking in server.xml. Because it's no longer needed for Controlpanel and the configuration differs between
  Tomcat 7 and Tomcat 8.
* Fixed wrong capitalization in server.xml for element host alias
* Merged all {stage}/ports files into one {home}/run/ports file.
* Support stages with empty MAVEN_HOME: Stool includes ${home}/maven-settings.xml now, they are used to resolve dependencies if
  the current user has no MAVEN_HOME.
* The separator for list property values is "," now, space is no longer supported.
* 'stool config': Display properties as strings, not as json objects.
* The 'tomcat.env' property is a map now, it defines the environment for applications.
  (Because the old white-listing is hard to debug and problematic when starting an application as a different user.)
* Removed the global 'errorTool' property, report problems to configured 'adminEmail' instead.
* 'stool select': Fixed illegal argument exception if the use specified an absolute path instead of stage name.
* Removed global 'portOverview' property.


### 3.2.0 (2015-08-09)

* Fix persistent ldap connection problem in overview.
* Renamed user "servlet" to "stool".
* Changed permission for stool files from 666 to 664.
* Properly set setgid bit for home directory and every stage directory.
*  Moved $STOOL_HOME/conf/overview.properties to $STOOL_HOME/overview.properties.
* Renamed $STOOL_HOME/wrappers to $STOOL_HOME/backstages.
* Renamed $STOOL_HOME/conf to $STOOL_HOME/run.
* Manual pages for all commands. puc help no longer contains command details, use man stool-&lt;command> instead.
* import: Fixed error message when argument is not a directory.
* Overview: Moved log output into tomcat/logs/overview.log.
* History command: support multiple details argument with ranges.
* History command: properly handle overlapping commands. To implement this i had to fix the log file format: id is unique
  accross file now, because a command started before midnight may finish the next day.
* stool -exception throws an intentional exception now. To test error tool.

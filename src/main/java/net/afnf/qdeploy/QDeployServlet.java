package net.afnf.qdeploy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * webapp for qdeploy-maven-plugin
 */
@SuppressWarnings("serial")
public class QDeployServlet extends javax.servlet.http.HttpServlet {

    private static Logger logger = Logger.getLogger(QDeployServlet.class.getName());

    protected InputStream inputStream;
    protected OutputStream outputStream;

    protected File qdeployDir;
    protected File qdeployJardir;
    protected File jarlistfile;
    protected String command;
    protected String wardir;
    protected String contextPath;
    protected String jarname;
    protected long lastmod;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {

            if (prepare(request, response) == false) {
                return;
            }

            if (StringUtils.equals(command, CommonUtil.CMD_JARLIST)) {
                cmdJarlist();
            }
            else if (StringUtils.equals(command, CommonUtil.CMD_PUTJAR)) {
                cmdPutjar();
            }
            else if (StringUtils.equals(command, CommonUtil.CMD_PUTWAR)) {
                cmdPutwar();
            }
        }
        catch (Exception e) {
            logger.severe(ExceptionUtils.getStackTrace(e));
            error(response, "error : " + e.toString());
        }
        finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
        }
    }

    protected boolean prepare(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String key = CommonUtil.getProperty("QDEPLOY_KEY");
        if (StringUtils.isBlank(key)) {
            error(response, "QDEPLOY_KEY is not defined");
            return false;
        }

        if (StringUtils.equals(key, request.getParameter("key")) == false) {
            error(response, "invalid key");
            return false;
        }

        wardir = CommonUtil.getProperty("QDEPLOY_WARDIR");
        if (StringUtils.isBlank(wardir)) {
            error(response, "QDEPLOY_WARDIR is not defined");
            return false;
        }

        File tmpdir = new File(System.getProperty("java.io.tmpdir"));
        if (tmpdir.exists() == false || tmpdir.isDirectory() == false) {
            error(response, "tmpdir does not exist :" + tmpdir.getAbsolutePath());
            return false;
        }

        contextPath = request.getParameter("path");
        if (StringUtils.isBlank(contextPath)) {
            error(response, "parameter 'path' is blank");
            return false;
        }

        command = request.getServletPath();
        if (StringUtils.indexOfAny(command, CommonUtil.CMDS) == -1) {
            error(response, "invalid command : " + command);
            return false;
        }

        if (StringUtils.equals(command, CommonUtil.CMD_PUTJAR)) {

            jarname = request.getParameter("jarname");
            if (StringUtils.isBlank(contextPath)) {
                error(response, "parameter 'path' is blank");
                return false;
            }

            String lastmodstr = request.getParameter("lastmod");
            if (StringUtils.isBlank(contextPath)) {
                error(response, "parameter 'lastmod' is blank");
                return false;
            }
            else if (StringUtils.isNumeric(lastmodstr) == false) {
                error(response, "parameter 'lastmod' is not numeric");
                return false;
            }
            lastmod = Long.parseLong(lastmodstr);
        }

        qdeployDir = new File(tmpdir, "qdeploy");
        qdeployJardir = new File(qdeployDir, contextPath + "-jar");
        if (qdeployJardir.exists() == false) {
            boolean ret = qdeployJardir.mkdirs();
            if (ret == false) {
                error(response, "fail to create qdeployJardir : " + qdeployJardir.getAbsolutePath());
                return false;
            }
        }
        jarlistfile = new File(qdeployJardir, "jarlist.txt");

        inputStream = request.getInputStream();
        outputStream = response.getOutputStream();

        return true;
    }

    protected void cmdJarlist() throws IOException {

        String jarliststr = IOUtils.toString(inputStream, CommonUtil.CHARSET);
        String[] jarlist = StringUtils.split(jarliststr, "\n");

        StringBuilder bufret = new StringBuilder();
        StringBuilder bufjarlist = new StringBuilder();
        int lacksize = 0;
        for (String jarinfo : jarlist) {
            String[] values = StringUtils.split(jarinfo, "\t");
            String jarname = values[0];
            long length = Long.parseLong(values[1]);
            long lastmod = Long.parseLong(values[2]);

            File jarfile = new File(qdeployJardir, jarname);
            boolean matched = jarfile.exists() && jarfile.length() == length
                    && (Math.abs(jarfile.lastModified() - lastmod) < 1000);
            if (matched == false) {
                lacksize++;
                bufret.append(jarname);
                bufret.append("\n");
            }

            bufjarlist.append(jarname);
            bufjarlist.append("\n");
        }

        FileUtils.write(jarlistfile, bufjarlist.toString());
        IOUtils.write(bufret.toString(), outputStream, CommonUtil.CHARSET);
        logger.info("jarlist OK, lack=" + lacksize);
    }

    protected void cmdPutjar() throws IOException {

        File jarfile = new File(qdeployJardir, jarname);
        FileUtils.writeByteArrayToFile(jarfile, IOUtils.toByteArray(inputStream));
        jarfile.setLastModified(lastmod);
        logger.info("putjar OK, jarname=" + jarname);
    }

    protected void cmdPutwar() throws IOException {

        File warfile = new File(wardir, contextPath + (contextPath.equals("/") ? "ROOT" : "") + ".war");

        String tmpname = contextPath + "_to";
        File tmpwar = new File(qdeployDir, tmpname + ".war");
        FileUtils.writeByteArrayToFile(tmpwar, IOUtils.toByteArray(inputStream));

        File tmpwarExtract = new File(qdeployDir, tmpname);
        CommonUtil.extractJarAll(tmpwar, tmpwarExtract);

        List<String> jarlist = FileUtils.readLines(jarlistfile, CommonUtil.CHARSET);
        for (String jarname : jarlist) {
            File jarfile = new File(qdeployJardir, jarname);
            FileUtils.copyFile(jarfile, new File(tmpwarExtract, "WEB-INF/lib/" + jarname));
        }

        CommonUtil.createJarFromDirectly(warfile, tmpwarExtract);
        logger.info("putwar OK");
    }

    protected void error(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        logger.severe(message);
        IOUtils.write("ERROR - " + message, outputStream, CommonUtil.CHARSET);
    }
}

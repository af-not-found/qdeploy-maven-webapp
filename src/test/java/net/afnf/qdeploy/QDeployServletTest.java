package net.afnf.qdeploy;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.afnf.FileTestUtil;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class QDeployServletTest {

    static File testBaseDir;
    static File fromDir;
    static File toDir;

    static String jarlist = "file1.jar\t54\t1390000000000\n" + "file2.jar\t1395359\t1390000001000\n"
            + "file3.jar\t10485760\t1390000002000\n" + "file4.jar\t10485759\t1390000003000\n";

    @BeforeClass
    static public void beforeClass() throws Exception {

        FileTestUtil.init();

        testBaseDir = new File(System.getProperty("java.io.tmpdir") + "/qdeploy-webapp-test");
        System.out.println(testBaseDir.getAbsolutePath());
        FileUtils.deleteDirectory(testBaseDir);

        fromDir = new File(testBaseDir, "from");
        toDir = new File(testBaseDir, "to");

        fromDir.mkdirs();
        toDir.mkdirs();
    }

    @AfterClass
    static public void afterClass() throws Exception {
        FileUtils.deleteDirectory(testBaseDir);
    }

    @Test
    public void test01_CmdJarlist_01() throws Exception {

        QDeployServlet qs = new QDeployServlet();
        qs.inputStream = IOUtils.toInputStream(jarlist);
        qs.outputStream = new ByteArrayOutputStream();
        qs.qdeployJardir = new File(testBaseDir, "jardir");
        qs.jarlistfile = new File(qs.qdeployJardir, "jarlist.txt");

        try {
            qs.cmdJarlist();

            String ret = new String(((ByteArrayOutputStream) qs.outputStream).toByteArray(), CommonUtil.CHARSET);
            assertEquals("file1.jar\nfile2.jar\nfile3.jar\nfile4.jar\n", ret);
        }
        finally {
            IOUtils.closeQuietly(qs.inputStream);
            IOUtils.closeQuietly(qs.outputStream);
        }
    }

    @Test
    public void test01_CmdJarlist_02() throws Exception {

        QDeployServlet qs = new QDeployServlet();
        qs.inputStream = IOUtils.toInputStream(jarlist);
        qs.outputStream = new ByteArrayOutputStream();
        qs.qdeployJardir = new File(testBaseDir, "jardir");
        qs.jarlistfile = new File(qs.qdeployJardir, "jarlist.txt");

        try {
            File f1 = FileTestUtil.writeFile("file1.jar", qs.qdeployJardir, 10, 54);
            File f2 = FileTestUtil.writeFile("file2.jar", qs.qdeployJardir, 1571, 1395359);
            File f3 = FileTestUtil.writeFile("file3.jar", qs.qdeployJardir, 0, FileTestUtil.LEN);

            f1.setLastModified(1390000000000L);
            f2.setLastModified(1390000001000L);
            f3.setLastModified(1388888888888L);

            qs.cmdJarlist();

            String ret = new String(((ByteArrayOutputStream) qs.outputStream).toByteArray(), CommonUtil.CHARSET);
            assertEquals("file3.jar\nfile4.jar\n", ret);
        }
        finally {
            IOUtils.closeQuietly(qs.inputStream);
            IOUtils.closeQuietly(qs.outputStream);
        }
    }

    @Test
    public void test02_CmdPutjar_01() throws Exception {

        QDeployServlet qs = new QDeployServlet();
        qs.inputStream = new ByteArrayInputStream(FileTestUtil.data, 0, FileTestUtil.LEN);
        qs.outputStream = new ByteArrayOutputStream();
        qs.qdeployJardir = new File(testBaseDir, "jardir");
        qs.jarname = "file3.jar";
        qs.lastmod = 1390000002000L;

        try {
            qs.cmdPutjar();

            File f = new File(qs.qdeployJardir, "file3.jar");
            assertEquals(FileTestUtil.LEN, f.length());
            assertEquals(qs.lastmod, f.lastModified());
        }
        finally {
            IOUtils.closeQuietly(qs.inputStream);
            IOUtils.closeQuietly(qs.outputStream);
        }
    }

    @Test
    public void test02_CmdPutjar_02() throws Exception {

        QDeployServlet qs = new QDeployServlet();
        qs.inputStream = new ByteArrayInputStream(FileTestUtil.data, 0, FileTestUtil.LEN - 1);
        qs.outputStream = new ByteArrayOutputStream();
        qs.qdeployJardir = new File(testBaseDir, "jardir");
        qs.jarname = "file4.jar";
        qs.lastmod = 1390000003000L;

        try {
            qs.cmdPutjar();

            File f = new File(qs.qdeployJardir, "file4.jar");
            assertEquals(FileTestUtil.LEN - 1, f.length());
            assertEquals(qs.lastmod, f.lastModified());
        }
        finally {
            IOUtils.closeQuietly(qs.inputStream);
            IOUtils.closeQuietly(qs.outputStream);
        }
    }

    @Test
    public void test03_CmdPutwar() throws Exception {

        File tmpwar = new File(testBaseDir, "tmp.war");
        {
            FileTestUtil.writeFile("WEB-INF/lib/file5.jar.bad", fromDir, 113450, 11);
            FileTestUtil.writeFile("WEB-INF/lib/subdir/file6.jar", fromDir, 113450, 1173);
            FileTestUtil.writeFile("WEB-INF/file7.jar", fromDir, 113451, 11733);
            FileTestUtil.writeFile("file8.jar", fromDir, 113452, 21734);

            CommonUtil.createJarFromDirectly(tmpwar, fromDir);
        }

        QDeployServlet qs = new QDeployServlet();
        qs.inputStream = FileUtils.openInputStream(tmpwar);
        qs.outputStream = new ByteArrayOutputStream();
        qs.wardir = toDir.getAbsolutePath();
        qs.qdeployDir = new File(testBaseDir, "deployDir");
        qs.qdeployJardir = new File(testBaseDir, "jardir");
        qs.contextPath = "webappname";
        qs.jarlistfile = new File(qs.qdeployJardir, "jarlist.txt");

        try {
            qs.cmdPutwar();

            File extractdir = new File(testBaseDir, "extractdir1");
            File finalwar = new File(qs.wardir, qs.contextPath + ".war");
            CommonUtil.extractJarAll(finalwar, extractdir);

            List<File> files = new ArrayList<File>(FileUtils.listFiles(extractdir, null, true));
            Collections.sort(files);
            assertEquals(8, files.size());
            int i = 0;
            assertEquals("file8.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/file7.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/lib/file1.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/lib/file2.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/lib/file3.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/lib/file4.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/lib/file5.jar.bad", CommonUtil.getRelativePath(extractdir, files.get(i++)));
            assertEquals("WEB-INF/lib/subdir/file6.jar", CommonUtil.getRelativePath(extractdir, files.get(i++)));

            FileUtils.copyFile(new File(qs.qdeployJardir, "file1.jar"), new File(fromDir, "WEB-INF/lib/file1.jar"), true);
            FileUtils.copyFile(new File(qs.qdeployJardir, "file2.jar"), new File(fromDir, "WEB-INF/lib/file2.jar"), true);
            FileUtils.copyFile(new File(qs.qdeployJardir, "file3.jar"), new File(fromDir, "WEB-INF/lib/file3.jar"), true);
            FileUtils.copyFile(new File(qs.qdeployJardir, "file4.jar"), new File(fromDir, "WEB-INF/lib/file4.jar"), true);

            FileTestUtil.assertDirEquals(fromDir, extractdir, false);
        }
        finally {
            IOUtils.closeQuietly(qs.inputStream);
            IOUtils.closeQuietly(qs.outputStream);
        }
    }
}

package net.sf.openrocket.file.rocksim.export;

import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.file.rocksim.importt.RocksimLoader;
import net.sf.openrocket.file.rocksim.importt.RocksimLoaderTest;
import net.sf.openrocket.file.rocksim.importt.RocksimTestBase;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;

/**
 */
public class RocksimDocumentDTOTest extends RocksimTestBase {
    
    @Test
    public void testDTO() throws Exception {
        JAXBContext binder = JAXBContext.newInstance(RocksimDocumentDTO.class);
        Marshaller marshaller = binder.createMarshaller();
        marshaller.setProperty("jaxb.fragment", Boolean.TRUE);

        NoseConeDTO noseCone = new NoseConeDTO();
        noseCone.setBaseDia(10d);
        noseCone.setCalcCG(1.3d);

        StageDTO stage1 = new StageDTO();
        stage1.addExternalPart(noseCone);

        RocketDesignDTO design2 = new RocketDesignDTO();
        design2.setName("Test");
        design2.setStage3(stage1);

        RocksimDesignDTO design = new RocksimDesignDTO();
        design.setDesign(design2);
        RocksimDocumentDTO message = new RocksimDocumentDTO();
        message.setDesign(design);


        StringWriter stringWriter = new StringWriter();
        marshaller.marshal(message, stringWriter);

        String response = stringWriter.toString();

        System.err.println(response);
    }
    
    @Test
    public void testRoundTrip() throws Exception {
        OpenRocketDocument ord = RocksimLoaderTest.loadRocksimRocket3(new RocksimLoader());
        
        Assert.assertNotNull(ord);
        String result = new RocksimSaver().marshalToRocksim(ord);

//        System.err.println(result);
        
        File output = new File("rt.rkt");
        FileWriter fw = new FileWriter(output);
        fw.write(result);
        fw.flush();
        fw.close();
    }
}

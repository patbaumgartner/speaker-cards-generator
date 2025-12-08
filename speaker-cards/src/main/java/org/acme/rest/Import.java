package org.acme.rest;

import org.acme.startup.ImportFromCSV;
import org.jboss.logging.Logger;

import io.quarkiverse.renarde.Controller;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/import")
public class Import extends Controller {

    private static final Logger LOG = Logger.getLogger(Import.class);

    @Inject
    ImportFromCSV importFromCSV;

    @GET
    @Path("/csv")
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public Response importCSV() {
        try {
            // Default XLSX file path in project root
            String xlsxPath = "SelectedWithSchedule.xlsx";
            importFromCSV.importFromCSV(xlsxPath);
            return Response.ok("XLSX import completed successfully. Check logs for details.").build();
        } catch (Exception e) {
            LOG.errorf(e, "Error during XLSX import");
            return Response.serverError()
                    .entity("Error during XLSX import: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/csv/{path:.*}")
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public Response importCSVFromPath(@jakarta.ws.rs.PathParam("path") String path) {
        try {
            importFromCSV.importFromCSV(path);
            return Response.ok("CSV import completed successfully. Check logs for details.").build();
        } catch (Exception e) {
            LOG.errorf(e, "Error during CSV import from path: %s", path);
            return Response.serverError()
                    .entity("Error during CSV import: " + e.getMessage())
                    .build();
        }
    }
}


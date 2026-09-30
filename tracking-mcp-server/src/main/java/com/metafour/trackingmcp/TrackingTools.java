package com.metafour.trackingmcp;

import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TrackingTools {

    private static final Logger log = LoggerFactory.getLogger(TrackingTools.class);

    private final TrackingService service;
    private final TrackingProperties props;

    public TrackingTools(TrackingService service, TrackingProperties props) {
        this.service = service;
        this.props = props;
    }

    @Tool(name = "get_parcel_tracking",
          description = """
                  Get the latest tracking information / status / event history of a parcel (consignment) \
                  from a courier system. Use when the user asks about a parcel, shipment, consignment or \
                  tracking number and mentions a system name (e.g. 'sparkle'). \
                  Extract the system name and the tracking number from the user's message. \
                  If the user separately asks for the delivery location or the signature/proof of delivery, \
                  use the get_delivery_proof tool instead (or afterwards) - it needs a postcode as well.""")
    public String getParcelTracking(
            @ToolParam(description = "Name of the tracking system, e.g. 'sparkle' from 'in sparkle system'. Lower-case sub-domain name only.")
            String system,
            @ToolParam(description = "The parcel tracking number, e.g. EX10004130651")
            String trackingNo) {
        try {
            return service.fetchTracking(system, trackingNo);
        } catch (IllegalArgumentException e) {
            return "Could not process request: " + e.getMessage();
        } catch (Exception e) {
            log.warn("Tracking fetch failed for {} / {}", system, trackingNo, e);
            return "Failed to fetch tracking from system '" + system + "': " + e.getMessage();
        }
    }

    @Tool(name = "get_delivery_proof",
          description = """
                  Get proof-of-delivery details for a parcel: delivery/collection location and the \
                  recipient's signature, from a courier system. Use when the user asks where a parcel was \
                  delivered, who signed for it, or for proof of delivery / POD. \
                  Requires the system name, the tracking number, and a postcode (the collection or delivery \
                  postcode for that parcel) - if the postcode is not given or known from earlier in the \
                  conversation, ask the user for it rather than guessing.""")
    public String getDeliveryProof(
            @ToolParam(description = "Name of the tracking system, e.g. 'sparkle'. Lower-case sub-domain name only.")
            String system,
            @ToolParam(description = "The parcel tracking number, e.g. EX10004130651")
            String trackingNo,
            @ToolParam(description = "The collection or delivery postcode for this parcel, e.g. 'W14 0HN'")
            String postcode) {
        try {
            return service.fetchDeliveryProof(system, trackingNo, postcode);
        } catch (IllegalArgumentException e) {
            return "Could not process request: " + e.getMessage();
        } catch (Exception e) {
            log.warn("Delivery-proof fetch failed for {} / {} / {}", system, trackingNo, postcode, e);
            return "Failed to fetch delivery proof from system '" + system + "': " + e.getMessage();
        }
    }

    @Tool(name = "list_tracking_systems",
          description = "List the tracking systems that are explicitly configured, and the default URL pattern used for other system names.")
    public String listTrackingSystems() {
        return "Configured systems: " + new TreeSet<>(props.systems().keySet())
                + "\nDefault pattern for any other system: " + props.baseUrlPattern()
                + "/online/tracking/trackinginfo/<trackingNo>";
    }
}
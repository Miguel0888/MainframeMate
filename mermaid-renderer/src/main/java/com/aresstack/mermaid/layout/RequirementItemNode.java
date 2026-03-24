package com.aresstack.mermaid.layout;

/**
 * A requirement diagram node with metadata.
 *
 * <h3>Mermaid source example</h3>
 * <pre>
 *   requirement user_login {
 *       id: 1
 *       text: Benutzeranmeldung
 *       risk: medium
 *       verifymethod: test
 *   }
 * </pre>
 */
public class RequirementItemNode extends DiagramNode {

    /** Requirement vs. element/component. */
    public enum ReqNodeType {
        REQUIREMENT, FUNCTIONAL_REQUIREMENT, INTERFACE_REQUIREMENT,
        PERFORMANCE_REQUIREMENT, DESIGN_CONSTRAINT, ELEMENT
    }

    private final ReqNodeType reqNodeType;
    private final String reqId;
    private final String risk;
    private final String verifyMethod;

    public RequirementItemNode(String id, String label,
                               double x, double y, double width, double height,
                               String svgId, ReqNodeType reqNodeType,
                               String reqId, String risk, String verifyMethod) {
        super(id, label, "requirement", x, y, width, height, svgId);
        this.reqNodeType = reqNodeType != null ? reqNodeType : ReqNodeType.REQUIREMENT;
        this.reqId = reqId != null ? reqId : "";
        this.risk = risk != null ? risk : "";
        this.verifyMethod = verifyMethod != null ? verifyMethod : "";
    }

    /** Type of this requirement node. */
    public ReqNodeType getReqNodeType() { return reqNodeType; }

    /** Requirement identifier (e.g. "1", "1.1"). */
    public String getReqId() { return reqId; }

    /** Risk level (low, medium, high). */
    public String getRisk() { return risk; }

    /** Verification method (test, analysis, inspection, demonstration). */
    public String getVerifyMethod() { return verifyMethod; }

    @Override
    public String toString() {
        return "RequirementItemNode{id='" + getId() + "', reqId='" + reqId
                + "', risk='" + risk + "', label='" + getLabel() + "'}";
    }
}


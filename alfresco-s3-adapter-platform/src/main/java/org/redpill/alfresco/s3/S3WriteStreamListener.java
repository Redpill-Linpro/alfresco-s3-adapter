package org.redpill.alfresco.s3;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;

/**
 * Stream listener which is used to copy the temp file contents into S3
 */
public class S3WriteStreamListener implements ContentStreamListener {

  private static final Log LOG = LogFactory.getLog(S3WriteStreamListener.class);

  private final S3ContentWriter writer;

  public S3WriteStreamListener(S3ContentWriter writer) {

    this.writer = writer;

  }

  @Override
  public void contentStreamClosed() throws ContentIOException {

    File file = writer.getTempFile();
    long size = file.length();
    writer.setSize(size);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Writing to s3://" + writer.getBucketName() + "/" + writer.getKey());
    }
    TransferManager transferManager = writer.getTransferManager();
    
    Upload upload;
    if (writer.getSSEAlgorithm() != null)
    {
    	PutObjectRequest putRequest = new PutObjectRequest(writer.getBucketName(), writer.getKey(), writer.getTempFile());
    	// Request server-side encryption.
    	ObjectMetadata objectMetadata = new ObjectMetadata();
    	objectMetadata.setSSEAlgorithm(writer.getSSEAlgorithm());   
    	putRequest.setMetadata(objectMetadata);
    	upload = transferManager.upload(putRequest);
    }
    else
    	upload = transferManager.upload(writer.getBucketName(), writer.getKey(), writer.getTempFile());
    //To have transactional consistency it is necessary to wait for the upload to go through before allowing the transaction to commit!
    try {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Waiting for upload result for bucket " + writer.getBucketName() + " with key " + writer.getKey());
      }
      upload.waitForUploadResult();
      if (LOG.isTraceEnabled()) {
        LOG.trace("Upload completed for bucket " + writer.getBucketName() + " with key " + writer.getKey());
      }
    } catch (Exception e) {
      throw new ContentIOException("S3WriterStreamListener Failed to Upload File for bucket " + writer.getBucketName() + " with key " + writer.getKey(), e);
    } finally {
      //Remove the temp file
      writer.getTempFile().delete();
    }

  }
}

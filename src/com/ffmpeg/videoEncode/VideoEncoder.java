package com.ffmpeg.videoEncode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.constants.AutoClassify;
import com.filenet.api.constants.AutoUniqueName;
import com.filenet.api.constants.CheckinType;
import com.filenet.api.constants.DefineSecurityParentage;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.ReferentialContainmentRelationship;
import com.filenet.api.engine.EventActionHandler;
import com.filenet.api.events.ObjectChangeEvent;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.property.FilterElement;
import com.filenet.api.property.Properties;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.util.Id;
import com.ibm.casemgmt.api.context.CaseMgmtContext;
import com.ibm.casemgmt.api.context.P8ConnectionCache;
import com.ibm.casemgmt.api.context.SimpleP8ConnectionCache;
import com.ibm.casemgmt.api.context.SimpleVWSessionCache;

import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.info.AudioInfo;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.info.VideoInfo;
import ws.schild.jave.info.VideoSize;

public class VideoEncoder implements EventActionHandler {

	public void onEvent(ObjectChangeEvent event, Id subId) throws EngineRuntimeException {
		// TODO Auto-generated method stub
		System.out.println("--%% Inside OnEvent Method Enter --%%");
		CaseMgmtContext origCmctx = null;
		try {
			System.out.println("Inside try");
			P8ConnectionCache connCache = new SimpleP8ConnectionCache();
			System.out.println("ConnCache: " + connCache.toString());
			origCmctx = CaseMgmtContext.set(new CaseMgmtContext(new SimpleVWSessionCache(), connCache));
			ObjectStore targetOS = event.getObjectStore();
			Id documentID = event.get_SourceObjectId();
			System.out.println("Document ID: " + documentID);
			Document document = fetchDocumentsAndSaveAsFiles(targetOS, documentID);
			if (document != null) {
				Properties documentProperties = document.getProperties();
				Folder folder = (Folder) documentProperties.getObjectValue("CmAcmAssociatedCase");
				System.out.println("Folder Name:" + folder.get_Name());
				String docClassName = document.getClassName();

				String documentTitle = document.get_Name();
				System.out.println("--%% Document Title: " + documentTitle);
				File sourceVideo = new File("C://Users//Administrator//Encode//" + documentTitle);
				String targetDocName = documentTitle.split("\\.")[0] + ".mp4";
				File targetVideo = new File("C://Users//Administrator//Encode//" + targetDocName);
				MultimediaObject sourceVideoMultiMediaObject = new MultimediaObject(sourceVideo);
				MultimediaInfo multimediaInfo = sourceVideoMultiMediaObject.getInfo();
				String sourceFormat = multimediaInfo.getFormat();
				System.out.println("Source Format: " + sourceFormat);
				AudioInfo audioInfo = multimediaInfo.getAudio();
				int audioBitRate = audioInfo.getBitRate();
				int audioChannels = audioInfo.getChannels();
				int audioSamplingRate = audioInfo.getSamplingRate();

				// Audio Attributes
				AudioAttributes audio = new AudioAttributes();
				audio.setCodec("aac");
				audio.setBitRate(new Integer(audioBitRate));
				audio.setChannels(new Integer(audioChannels));
				audio.setSamplingRate(new Integer(audioSamplingRate));

				VideoInfo videoInfo = multimediaInfo.getVideo();
				int videoBitRate = videoInfo.getBitRate();
				VideoSize videoSize = videoInfo.getSize();
				int videoFrameRate = (int) videoInfo.getFrameRate();

				// Video Attributes
				VideoAttributes video = new VideoAttributes();
				video.setCodec("libx264");
				video.setBitRate(new Integer(videoBitRate));
				video.setSize(videoSize);
				video.setFrameRate(new Integer(videoFrameRate));

				// Encoding attributes
				EncodingAttributes encodingAttributes = new EncodingAttributes();
				encodingAttributes.setMapMetaData(true);
				encodingAttributes.setInputFormat(sourceFormat);
				encodingAttributes.setOutputFormat("mp4");
				encodingAttributes.setAudioAttributes(audio);
				encodingAttributes.setVideoAttributes(video);

				// Encode
				System.out.println("Encode Starts");
				Encoder encoder = new Encoder();
				encoder.encode(sourceVideoMultiMediaObject, targetVideo, encodingAttributes);
				InputStream targetVideoInputStream = new FileInputStream(targetVideo);
				Document convertedDocument = Factory.Document.createInstance(targetOS, docClassName);
				ContentElementList contentList = Factory.ContentElement.createList();
				ContentTransfer contentTransfer = Factory.ContentTransfer.createInstance();
				contentTransfer.setCaptureSource(targetVideoInputStream);
				contentTransfer.set_RetrievalName(targetDocName);
				contentTransfer.set_ContentType("video/mp4");
				contentList.add(contentTransfer);

				convertedDocument.set_ContentElements(contentList);
				convertedDocument.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
				Properties p = convertedDocument.getProperties();
				p.putValue("DocumentTitle", targetDocName);
				convertedDocument.setUpdateSequenceNumber(null);
				convertedDocument.save(RefreshMode.REFRESH);
				ReferentialContainmentRelationship rc = folder.file(convertedDocument, AutoUniqueName.AUTO_UNIQUE,
						targetDocName, DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
				rc.save(RefreshMode.REFRESH);
			} else {
				System.out.println("**--Source Video is an MP4 file--**");
			}
		} catch (Exception e) {
			System.out.println("Inside Catch");
			System.out.println(e);
			throw new RuntimeException(e);
		} finally {
			CaseMgmtContext.set(origCmctx);
		}
	}

	private Document fetchDocumentsAndSaveAsFiles(ObjectStore targetOS, Id documentID) throws Exception {
		System.out.println("%% Inside fetchDocumentsAndSaveAsFiles %%");
		String docTitle = "";
		Document doc = null;
		try {
			FilterElement fe = new FilterElement(null, null, null, "Owner Name", null);
			FilterElement associatedCase = new FilterElement(null, null, null, "CmAcmAssociatedCase", null);
			PropertyFilter pf = new PropertyFilter();
			pf.addIncludeProperty(new FilterElement(null, null, null, PropertyNames.CONTENT_SIZE, null));
			pf.addIncludeProperty(new FilterElement(null, null, null, PropertyNames.CONTENT_ELEMENTS, null));
			pf.addIncludeProperty(new FilterElement(null, null, null, PropertyNames.FOLDERS_FILED_IN, null));
			pf.addIncludeProperty(fe);
			pf.addIncludeProperty(associatedCase);
			doc = Factory.Document.fetchInstance(targetOS, documentID, pf);
			docTitle = doc.get_Name();
			System.out.println("--%%-- Document Title: " + docTitle);
			String format = docTitle.split("\\.")[1];
			if ("mp4".equals(format)) {
				return null;
			} else {
				ContentElementList docContentList = doc.get_ContentElements();
				Iterator iter = docContentList.iterator();
				while (iter.hasNext()) {
					ContentTransfer ct = (ContentTransfer) iter.next();
					InputStream stream = ct.accessContentStream();
					File documentFile = new File("C://Users//Administrator//Encode//" + docTitle);
					Path path = Paths.get(documentFile.toURI());
					Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
			throw new Exception(e);
		}
		return doc;

	}

}

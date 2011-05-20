package placebooks.controller;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Iterator;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.NoResultException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.log4j.Logger;

import org.springframework.security.authentication.encoding.Md5PasswordEncoder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import placebooks.model.AudioItem;
import placebooks.model.GPSTraceItem;
import placebooks.model.ImageItem;
import placebooks.model.PlaceBook;
import placebooks.model.PlaceBookItem;
import placebooks.model.PlaceBookItemSearchIndex;
import placebooks.model.PlaceBookSearchIndex;
import placebooks.model.TextItem;
import placebooks.model.User;
import placebooks.model.VideoItem;
import placebooks.model.WebBundleItem;
import placebooks.model.json.Shelf;
import placebooks.utils.InitializeDatabase;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

// TODO: general todo is to do file checking to reduce unnecessary file writes, 
// part of which is ensuring new file writes in cases of changes
// 
// TODO: stop orphan / null field elements being added to database

@Controller
public class PlaceBooksAdminController
{
	private static final Logger log = 
		Logger.getLogger(PlaceBooksAdminController.class.getName());

	@RequestMapping(value = "/palette", method = RequestMethod.GET)
	public void getPaletteItemsJSON(final HttpServletResponse res)
	{
		final EntityManager manager = EMFSingleton.getEntityManager();
		final User user = UserManager.getCurrentUser(manager);
		final TypedQuery<PlaceBookItem> q = manager
				.createQuery(	"SELECT p FROM PlaceBookItem p WHERE p.owner= :owner AND p.placebook IS NULL",
								PlaceBookItem.class);
		q.setParameter("owner", user);

		final Collection<PlaceBookItem> pbs = q.getResultList();
		log.info("Converting " + pbs.size() + " PlaceBooks to JSON");
		log.info("User " + user.getName());
		try
		{
			final ObjectMapper mapper = new ObjectMapper();
			log.info("Shelf: " + mapper.writeValueAsString(pbs));
			final ServletOutputStream sos = res.getOutputStream();
			res.setContentType("application/json");
			mapper.writeValue(sos, pbs);
			sos.flush();
		}
		catch (final Exception e)
		{
			log.error(e.toString());
		}

		manager.close();
	}

	@RequestMapping(value = "/placebook/{key}", method = RequestMethod.GET)
	public void getPlaceBookJSON(final HttpServletResponse res, @PathVariable("key") final String key)
	{
		final EntityManager manager = EMFSingleton.getEntityManager();
		try
		{
			final PlaceBook placebook = manager.find(PlaceBook.class, key);
			if (placebook != null)
			{
				try
				{
					final ObjectMapper mapper = new ObjectMapper();
					mapper.getSerializationConfig().setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);					
					final ServletOutputStream sos = res.getOutputStream();
					res.setContentType("application/json");
					mapper.writeValue(sos, placebook);
					log.info("Placebook: " + mapper.writeValueAsString(placebook));
					sos.flush();
				}
				catch (final IOException e)
				{
					log.error(e.toString());
				}
			}
		}
		catch (final Throwable e)
		{
			log.error(e.getMessage(), e);
		}
		finally
		{
			manager.close();
		}
	}

	@RequestMapping(value = "/shelf", method = RequestMethod.GET)
	public void getPlaceBooksJSON(final HttpServletRequest req, final HttpServletResponse res)
	{
		final EntityManager manager = EMFSingleton.getEntityManager();
		try
		{
			final User user = UserManager.getCurrentUser(manager);
			if (user != null)
			{
				final TypedQuery<PlaceBook> q = manager.createQuery("SELECT p FROM PlaceBook p WHERE p.owner= :owner",
																	PlaceBook.class);
				q.setParameter("owner", user);

				final Collection<PlaceBook> pbs = q.getResultList();
				log.info("Converting " + pbs.size() + " PlaceBooks to JSON");
				log.info("User " + user.getName());
				try
				{
					final Shelf shelf = new Shelf(user, pbs);
					final ObjectMapper mapper = new ObjectMapper();
					log.info("Shelf: " + mapper.writeValueAsString(shelf));
					final ServletOutputStream sos = res.getOutputStream();
					res.setContentType("application/json");
					mapper.writeValue(sos, shelf);
					sos.flush();
				}
				catch (final Exception e)
				{
					log.error(e.toString());
				}
			}
			else
			{
				try
				{
					final ObjectMapper mapper = new ObjectMapper();
					final ServletOutputStream sos = res.getOutputStream();
					mapper.writeValue(sos, req.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION)
							.toString());
					sos.flush();
				}
				catch (final IOException e)
				{
					log.error(e.getMessage(), e);
				}
			}
		}
		finally
		{
			manager.close();
		}
	}

	@RequestMapping(value = "/account", method = RequestMethod.GET)
	public String accountPage()
	{
		return "account";
	}

	@RequestMapping(value = "/createUserAccount", method = RequestMethod.POST)
	public String createUserAccount(@RequestParam final String name, @RequestParam final String email,
			@RequestParam final String password)
	{
		final Md5PasswordEncoder encoder = new Md5PasswordEncoder();
		final User user = new User(name, email, encoder.encodePassword(password, null));

		final EntityManager manager = EMFSingleton.getEntityManager();
		try
		{
			manager.getTransaction().begin();
			manager.persist(user);
			manager.getTransaction().commit();
		}
		catch (final Exception e)
		{
			log.error("Error creating user", e);
		}
		finally
		{
			if (manager.getTransaction().isActive())
			{
				manager.getTransaction().rollback();
				log.error("Rolling back user creation");
			}
			manager.close();
		}

		return "redirect:/login.html";
	}

	@RequestMapping(value = "/currentUser", method = RequestMethod.GET)
	public void currentUser(final HttpServletRequest req, final HttpServletResponse res)
	{
		res.setContentType("application/json");
		final EntityManager entityManager = EMFSingleton.getEntityManager();
		try
		{
			final User user = UserManager.getCurrentUser(entityManager);
			if (user == null)
			{

			}
			else
			{
				try
				{
					final ObjectMapper mapper = new ObjectMapper();
					final ServletOutputStream sos = res.getOutputStream();
					mapper.writeValue(sos, user);
					log.info("User: " + mapper.writeValueAsString(user));
					sos.flush();
				}
				catch (final IOException e)
				{
					log.error(e.getMessage(), e);
				}
			}
		}
		finally
		{
			if (entityManager.getTransaction().isActive())
			{
				entityManager.getTransaction().rollback();
			}
			entityManager.close();
		}
	}
	

	private boolean containsItem(final PlaceBookItem findItem, final List<PlaceBookItem> items)
	{
		for (final PlaceBookItem item : items)
		{
			if (findItem.getKey().equals(item.getKey())) { return true; }
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/saveplacebook", method = RequestMethod.POST)
	public void savePlaceBookJSON(final HttpServletResponse res, @RequestParam("placebook") final String json)
	{
		log.info("Save Placebook: " + json);
		final ObjectMapper mapper = new ObjectMapper();
		mapper.getSerializationConfig().setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);		
		final EntityManager manager = EMFSingleton.getEntityManager();				
		manager.getTransaction().begin();
		try
		{
			final PlaceBook placebook = mapper.readValue(json, PlaceBook.class);

			if (placebook.getKey() != null)
			{
				final PlaceBook dbPlacebook = manager.find(PlaceBook.class, placebook.getKey());
				if (dbPlacebook != null)
				{
					// Remove any items that are no longer used
					Map<String, PlaceBookItemSearchIndex> indices = new HashMap<String, PlaceBookItemSearchIndex>();
					for (final PlaceBookItem item : dbPlacebook.getItems())
					{
						if (!containsItem(item, placebook.getItems()))
						{
							manager.remove(item);
						}
						else
						{
							indices.put(item.getKey(), item.getSearchIndex());
						}
					}

					dbPlacebook.setItems(Collections.EMPTY_LIST);
					for (final PlaceBookItem item : placebook.getItems())
					{
						if(item.getKey() != null)
						{
							item.getSearchIndex().setID(indices.get(item.getKey()).getID());
						}

						for(Entry<String, String> metadataItem: item.getMetadata().entrySet())
						{
							item.addMetadataEntry(metadataItem.getKey(), metadataItem.getValue());
						}

						item.setOwner(dbPlacebook.getOwner());

						if(item.getTimestamp() == null)
						{
							item.setTimestamp(new Date());
						}

						dbPlacebook.addItem(item);
					}

					for (final Entry<String, String> entry : placebook.getMetadata().entrySet())
					{
						dbPlacebook.addMetadataEntry(entry.getKey(), entry.getValue());
					}

					dbPlacebook.setGeometry(placebook.getGeometry());

					manager.merge(dbPlacebook);
					log.info("Updated PlaceBook: " + mapper.writeValueAsString(dbPlacebook));
				}
				else
				{
					placebook.setOwner(UserManager.getCurrentUser(manager));
					manager.persist(placebook);
					log.info("Added PlaceBook: " + mapper.writeValueAsString(placebook));					
				}
			}
			else
			{
				placebook.setOwner(UserManager.getCurrentUser(manager));
				manager.persist(placebook);
				log.info("Added PlaceBook: " + mapper.writeValueAsString(placebook));
			}

			manager.getTransaction().commit();

			res.setContentType("application/json");				
			final ServletOutputStream sos = res.getOutputStream();
			final PlaceBook resultPlacebook = manager.find(PlaceBook.class, placebook.getKey());			
			mapper.writeValue(sos, resultPlacebook);
			sos.flush();			
		}
		catch (final Throwable e)
		{
			log.warn(e.getMessage(), e);
		}
		finally
		{
			if (manager.getTransaction().isActive())
			{
				manager.getTransaction().rollback();
			}
			manager.close();
		}
	}


	@RequestMapping(value = "/admin/add_placebook", method = RequestMethod.POST)
	public ModelAndView addPlaceBook(@RequestParam final String owner, 
									 @RequestParam final String geometry)
	{
		if (owner != null)
		{
			Geometry geometry_ = null;
			try
			{
				geometry_ = new WKTReader().read(geometry);
			}
			catch (final ParseException e)
			{
				log.error(e.toString());
			}

			// If created inside getting the EntityManager, some fields are
			// null. Not sure why... TODO
			final PlaceBook p = new PlaceBook(null, geometry_);

			final EntityManager pm = EMFSingleton.getEntityManager();

			final User owner_ = UserManager.getUser(pm, owner);

			if (owner_ == null) 
			{ 
				return new ModelAndView("message", "text", 
										"User does not exist"); 
			}

			p.setOwner(owner_);

			try
			{
				pm.getTransaction().begin();
				pm.persist(p);
				pm.getTransaction().commit();
			}
			finally
			{
				if (pm.getTransaction().isActive())
				{
					pm.getTransaction().rollback();
					log.error("Rolling current persist transaction back");
				}
				pm.close();
			}

			return new ModelAndView("message", "text", "PlaceBook added");
		}
		else
		{
			return new ModelAndView("message", "text", "Error in POST");
		}
	}

	@RequestMapping(value = "/admin/add_placebookitem_mapping/{type}", 
					method = RequestMethod.POST)
	public ModelAndView addPlaceBookItemMapping(
		@RequestParam final String key, @RequestParam final String mKey,
		@RequestParam final String mValue, 
		@PathVariable("type") final String type)
	{
		if (!type.equals("metadata") && !type.equals("parameter"))
			return new ModelAndView("message", "text", "Error in type");

		if (key != null && mKey != null && mValue != null)
		{
			final EntityManager pm = EMFSingleton.getEntityManager();

			try
			{
				pm.getTransaction().begin();
				final PlaceBookItem p = pm.find(PlaceBookItem.class, key);
				if (type.equals("metadata"))
				{
					p.addMetadataEntry(mKey, mValue);
				}
				else if (type.equals("parameter"))
				{
					int iValue = -1;
					try
					{
						iValue = Integer.parseInt(mValue);
					}
					catch (final NumberFormatException e)
					{
						log.error("Error parsing parameter data value");
					}

					p.addParameterEntry(mKey, new Integer(iValue));
				}
				pm.getTransaction().commit();
			}
			finally
			{
				if (pm.getTransaction().isActive())
				{
					pm.getTransaction().rollback();
					log.error("Rolling current persist transaction back");
				}
				pm.close();
			}

			return new ModelAndView("message", "text", "Metadata/param added");
		}
		else
		{
			return new ModelAndView("message", "text", "Error in POST");
		}
	}

	@RequestMapping(value = "/admin/add_placebook_metadata", 
					method = RequestMethod.POST)
	public ModelAndView addPlaceBookMetadata(@RequestParam final String key, 
											 @RequestParam final String mKey,
								 			 @RequestParam final String mValue)
	{
		if (key != null && mKey != null && mValue != null)
		{
			final EntityManager pm = EMFSingleton.getEntityManager();

			try
			{
				pm.getTransaction().begin();
				final PlaceBook p = pm.find(PlaceBook.class, key);
				p.addMetadataEntry(mKey, mValue);
				pm.getTransaction().commit();
			}
			finally
			{
				if (pm.getTransaction().isActive())
				{
					pm.getTransaction().rollback();
					log.error("Rolling current persist transaction back");
				}

				pm.close();
			}
			return new ModelAndView("message", "text", "Metadata added");
		}
		else
		{
			return new ModelAndView("message", "text", "Error in POST");
		}
	}

	@RequestMapping(value = "/admin", method = RequestMethod.GET)
	public String adminPage()
	{
		return "admin";
	}

	@RequestMapping(value = "/placebook/{key}", method = RequestMethod.GET)
	public ModelAndView getPlaceBookJSON(final HttpServletRequest req, 
										 final HttpServletResponse res,
					@PathVariable("key") final String key)
	{
		final EntityManager manager = EMFSingleton.getEntityManager();
		try
		{
			PlaceBook placebook = null;
			if (key.equals("new"))
			{
				// Create new placebook
				placebook = new PlaceBook(UserManager.getCurrentUser(manager), 
										  null);
				manager.persist(placebook);
			}
			else
			{
				placebook = manager.find(PlaceBook.class, key);
			}

			if (placebook != null)
			{
				try
				{
					final ObjectMapper mapper = new ObjectMapper();
					final ServletOutputStream sos = res.getOutputStream();
					res.setContentType("application/json");
					mapper.writeValue(sos, placebook);
					log.info("Placebook: " + 
						mapper.writeValueAsString(placebook));
					sos.flush();
				}
				catch (final IOException e)
				{
					log.error(e.toString());
				}
			}
		}
		catch (final Throwable e)
		{
			log.error(e.getMessage(), e);
		}
		finally
		{
			manager.close();
		}

		return null;
	}

	@RequestMapping(value = "/admin/add_item/webbundle", 
					method = RequestMethod.POST)
	@SuppressWarnings("unchecked")
	public ModelAndView createWebBundle(final HttpServletRequest req)
	{
		final EntityManager pm = EMFSingleton.getEntityManager();

		final ItemData itemData = new ItemData();
		WebBundleItem wbi = null;

		try
		{
			pm.getTransaction().begin();

			for (final Enumeration<String> params = req.getParameterNames(); 
				 params.hasMoreElements();)
			{
				final String param = params.nextElement();
				final String value = req.getParameterValues(param)[0];
				if (!processItemData(itemData, pm, param, value))
				{
					String[] split = getExtension(param);
					if (split == null)
						continue;

					String prefix = split[0], suffix = split[1];

					if (prefix.contentEquals("url"))
					{
						try
						{
							final PlaceBook p = pm.find(PlaceBook.class, 
														suffix);
							URL sourceURL = null;
							if (value.length() > 0)
							{
								sourceURL = new URL(value);
							}
							wbi = new WebBundleItem(null, null, sourceURL, 
													new File(""));
							p.addItem(wbi);
						}
						catch (final java.net.MalformedURLException e)
						{
							log.error(e.toString());
						}
					}
				}

			}

			if (wbi != null)
			{
				wbi.setOwner(itemData.getOwner());
				wbi.setGeometry(itemData.getGeometry());
			}

			if (wbi == null || (wbi != null && (wbi.getSourceURL() == null || 
				wbi.getOwner() == null))) 
			{ 
				return new ModelAndView(
					"message", "text", "Error setting data elements"); 
			}

			PlaceBooksAdminHelper.scrape(wbi);

			pm.getTransaction().commit();
		}
		finally
		{
			if (pm.getTransaction().isActive())
			{
				pm.getTransaction().rollback();
				log.error("Rolling current persist transaction back");
			}

			pm.close();
		}

		return new ModelAndView("message", "text", "Scraped");
	}


	@RequestMapping(value = "/admin/delete_placebook/{key}", 
					method = RequestMethod.GET)
	public ModelAndView deletePlaceBook(@PathVariable("key") final String key)
	{

		final EntityManager pm = EMFSingleton.getEntityManager();

		try
		{
			pm.getTransaction().begin();
			final PlaceBook p = pm.find(PlaceBook.class, key);
			pm.remove(p);
			pm.getTransaction().commit();
		}
		finally
		{
			if (pm.getTransaction().isActive())
			{
				pm.getTransaction().rollback();
				log.error("Rolling current delete single transaction back");
			}

			pm.close();
		}

		log.info("Deleted PlaceBook");

		return new ModelAndView("message", "text", "Deleted PlaceBook: " + key);
	}

	@RequestMapping(value = "/admin/delete_placebookitem/{key}", 
					method = RequestMethod.GET)
	public ModelAndView deletePlaceBookItem(@PathVariable("key") 
											final String key)
	{

		final EntityManager pm = EMFSingleton.getEntityManager();

		try
		{
			pm.getTransaction().begin();
			final PlaceBookItem item = pm.find(PlaceBookItem.class, key);
			pm.remove(item);
			pm.getTransaction().commit();
		}
		finally
		{
			if (pm.getTransaction().isActive())
			{
				pm.getTransaction().rollback();
				log.error("Rolling current delete single transaction back");
			}
			pm.close();
		}

		log.info("Deleted PlaceBookItem " + key);

		return new ModelAndView("message", "text", "Deleted PlaceBookItem: " 
								+ key);

	}
	
	
	@RequestMapping(value = "/admin/shelf/{owner}", method = RequestMethod.GET)
	public ModelAndView getPlaceBooksJSON(final HttpServletRequest req, 
										  final HttpServletResponse res,
				   @PathVariable("owner") final String owner)
	{
		if (owner.trim().isEmpty())
			return null;

		final EntityManager pm = EMFSingleton.getEntityManager();
		final TypedQuery<User> uq = 
			pm.createQuery("SELECT u FROM User u WHERE u.email LIKE :email", 
						   User.class);
		uq.setParameter("email", owner.trim());
		try
		{
			final User user = uq.getSingleResult();

			final TypedQuery<PlaceBook> q = 
				pm.createQuery(
					"SELECT p FROM PlaceBook p WHERE p.owner = :user",
					PlaceBook.class
				);

			q.setParameter("user", user);
			final Collection<PlaceBook> pbs = q.getResultList();

			log.info("Converting " + pbs.size() + " PlaceBooks to JSON");
			if (!pbs.isEmpty())
			{
				final Shelf s = new Shelf(user, pbs);
				try
				{
					final ObjectMapper mapper = new ObjectMapper();
					final ServletOutputStream sos = res.getOutputStream();
					res.setContentType("application/json");
					mapper.writeValue(sos, s);
					sos.flush();
				}
				catch (final IOException e)
				{
					log.error(e.toString());
				}
			}

		}
		catch (NoResultException e)
		{
			log.error(e.toString());
		}
		finally
		{
			pm.close();
		}

		return null;
	}

	@RequestMapping(value = "/admin/package/{key}", method = RequestMethod.GET)
	public ModelAndView makePackage(final HttpServletRequest req, 
									final HttpServletResponse res,
		  	   @PathVariable("key") final String key)
	{
		final EntityManager pm = EMFSingleton.getEntityManager();

		final PlaceBook p = pm.find(PlaceBook.class, key);
		final File zipFile = PlaceBooksAdminHelper.makePackage(p);
		if (zipFile == null)
		{
			return new ModelAndView("message", "text", 
									"Making and compressing package");
		}
			
		try 
		{
			// Serve up file from disk
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			final FileInputStream fis = new FileInputStream(zipFile);
			final BufferedInputStream bis = new BufferedInputStream(fis);

			final byte data[] = new byte[2048];
			int i;
			while ((i = bis.read(data, 0, 2048)) != -1)
			{
				bos.write(data, 0, i);
			}
			fis.close();

			final ServletOutputStream sos = res.getOutputStream();
			res.setContentType("application/zip");
			res.setHeader("Content-Disposition", "attachment; filename=\"" 
						  + p.getKey() + ".zip\"");
			sos.write(bos.toByteArray());
			sos.flush();

		}
		catch (final IOException e)
		{
			log.error(e.toString());
			return new ModelAndView("message", "text", "Error sending package");
		}
		finally
		{
			pm.close();
		}

		return null;
	}

	
	@RequestMapping(value = "/admin/search/{terms}", method = RequestMethod.GET)
	public ModelAndView searchGET(@PathVariable("terms") final String terms)
	{
		final long timeStart = System.nanoTime();
		final long timeEnd;


		final StringBuffer out = new StringBuffer();
		for (final Map.Entry<PlaceBook, Integer> entry : 
			 PlaceBooksAdminHelper.search(terms))
		{
			out.append("key=" + entry.getKey().getKey() + ", score=" 
					   + entry.getValue() + "<br>");
		}

		timeEnd = System.nanoTime();
		out.append("<br>Execution time = " + (timeEnd - timeStart) + " ns");

		return new ModelAndView("message", "text", "search results:<br>" 
								+ out.toString());
	}

	@RequestMapping(value = "/admin/search", method = RequestMethod.POST)
	public ModelAndView searchPOST(final HttpServletRequest req)
	{
		final StringBuffer out = new StringBuffer();
		final String[] terms = req.getParameter("terms").split("\\s");
		for (int i = 0; i < terms.length; ++i)
		{
			out.append(terms[i]);
			if (i < terms.length - 1)
			{
				out.append("+");
			}
		}

		return searchGET(out.toString());
	}

	@RequestMapping(value = "/admin/add_item/upload", 
					method = RequestMethod.POST)
	public ModelAndView uploadFile(final HttpServletRequest req)
	{
		final EntityManager pm = EMFSingleton.getEntityManager();

		final ItemData itemData = new ItemData();
		PlaceBookItem pbi = null;

		try
		{
			pm.getTransaction().begin();

			final FileItemIterator i = 
				new ServletFileUpload().getItemIterator(req);
			while (i.hasNext())
			{
				final FileItemStream item = i.next();
				if (item.isFormField())
				{
					processItemData(itemData, pm, item.getFieldName(), 
									Streams.asString(item.openStream()));
				}
				else
				{
					String property = null;
				
					String[] split = getExtension(item.getFieldName());
					if (split == null)
						continue;

					String prefix = split[0], suffix = split[1];

					final PlaceBook p = pm.find(PlaceBook.class, suffix);

					if (prefix.contentEquals("gpstrace"))
					{
						Document gpxDoc = null;
						// StringReader reader = new StringReader(value);
						final InputStream reader = item.openStream();
						final InputSource source = new InputSource(reader);
						final DocumentBuilder builder = 
							DocumentBuilderFactory
								.newInstance()
								.newDocumentBuilder();
						gpxDoc = builder.parse(source);
						reader.close();
						pbi = new GPSTraceItem(null, null, null, gpxDoc);
						p.addItem(pbi);

						continue;
					}
					else if (!prefix.contentEquals("video") &&
							 !prefix.contentEquals("audio") && 
							 !prefix.contentEquals("image"))
					{
						throw new Exception("Unsupported file type");
					}

					final String path = 
						PropertiesSingleton
							.get(this.getClass().getClassLoader())
							.getProperty(PropertiesSingleton.IDEN_MEDIA, "");

					if (!new File(path).exists() && !new File(path).mkdirs()) 
					{
						throw new Exception("Failed to write file"); 
					}

					File file = null;

					final int extIdx = item.getName().lastIndexOf(".");
					final String ext = 
						item.getName().substring(extIdx + 1, 
												 item.getName().length());

					if (prefix.contentEquals("video"))
					{
						pbi = new VideoItem(null, null, null, new File(""));
						p.addItem(pbi);
						pm.getTransaction().commit();
						pm.getTransaction().begin();
						((VideoItem) pbi).setVideo(path + "/" + pbi.getKey() 
												   + "." + ext);

						file = new File(((VideoItem) pbi).getVideo());
					}
					else if (prefix.contentEquals("audio"))
					{
						pbi = new AudioItem(null, null, null, new File(""));
						p.addItem(pbi);
						pm.getTransaction().commit();
						pm.getTransaction().begin();
						((AudioItem) pbi).setAudio(path + "/" + pbi.getKey() 
												   + "." + ext);

						file = new File(((AudioItem) pbi).getAudio());
					}
					else if (prefix.contentEquals("image"))
					{
						pbi = new ImageItem(null, null, null, new File(""));
						p.addItem(pbi);
						pm.getTransaction().commit();
						pm.getTransaction().begin();
						((ImageItem)pbi).setImage(path + "/" + pbi.getKey()
												  + "." + ext);
						file = new File(((ImageItem)pbi).getImage());
					}

					final InputStream input = item.openStream();
					final OutputStream output = new FileOutputStream(file);
					int byte_;
					while ((byte_ = input.read()) != -1)
					{
						output.write(byte_);
					}
					output.close();
					input.close();

					log.info("Wrote " + prefix + " file " 
							 + file.getAbsolutePath());

				}

			}

			if (pbi == null || itemData.getOwner() == null) 
			{ 
				throw new Exception("Error setting data elements"); 
			}

			pbi.setOwner(itemData.getOwner());
			pbi.setSourceURL(itemData.getSourceURL());
			pbi.setGeometry(itemData.getGeometry());

			pm.getTransaction().commit();
		}
		catch (final FileUploadException e)
		{
			log.error(e.toString());
		}
		catch (final ParserConfigurationException e)
		{
			log.error(e.toString());
		}
		catch (final SAXException e)
		{
			log.error(e.toString());
		}
		catch (final IOException e)
		{
			log.error(e.toString());
		}
		catch (final Exception e)
		{
			log.error(e.toString());
		}
		finally
		{
			if (pm.getTransaction().isActive())
			{
				pm.getTransaction().rollback();
				log.error("Rolling current persist transaction back");
			}

			pm.close();
		}

		return new ModelAndView("message", "text", "Done");
	}

	@RequestMapping(value = "/admin/add_item/text", method = RequestMethod.POST)
	@SuppressWarnings("unchecked")
	public ModelAndView uploadText(final HttpServletRequest req)
	{
		final EntityManager pm = EMFSingleton.getEntityManager();

		final ItemData itemData = new ItemData();
		PlaceBookItem pbi = null;

		try
		{
			pm.getTransaction().begin();

			for (final Enumeration<String> params = req.getParameterNames(); 
				 params.hasMoreElements();)
			{
				final String param = params.nextElement();
				final String value = req.getParameterValues(param)[0];
				if (!processItemData(itemData, pm, param, value))
				{
					String[] split = getExtension(param);
					if (split == null)
						continue;

					String prefix = split[0], suffix = split[1];

					final PlaceBook p = pm.find(PlaceBook.class, suffix);

					if (prefix.contentEquals("text"))
					{
						String value_ = null;
						if (value.length() > 0)
						{
							value_ = value;
						}
						pbi = new TextItem(null, null, null, value_);
						p.addItem(pbi);
					}
				}

			}

			if ((pbi != null && ((TextItem) pbi).getText() == null) || 
				pbi == null || itemData.getOwner() == null) 
			{ 
				return new ModelAndView(
					"message", "text", "Error setting data elements"); 
			}

			pbi.setOwner(itemData.getOwner());
			pbi.setGeometry(itemData.getGeometry());
			pbi.setSourceURL(itemData.getSourceURL());

			pm.getTransaction().commit();
		}
		finally
		{
			if (pm.getTransaction().isActive())
			{
				pm.getTransaction().rollback();
				log.error("Rolling current persist transaction back");
			}
			pm.close();
		}

		return new ModelAndView("message", "text", "TextItem added");
	}


	@RequestMapping(value = "/admin/serve/gpstraceitem/{key}", 
					method = RequestMethod.GET)
	public ModelAndView serveGPSTraceItem(final HttpServletRequest req, 
								   	      final HttpServletResponse res,
								   	      @PathVariable("key") final String key)
	{
		final EntityManager em = EMFSingleton.getEntityManager();

		try
		{
			final GPSTraceItem g = em.find(GPSTraceItem.class, key);

			if (g != null)
			{
				final String trace = g.getTrace();
				
				res.setContentType("text/xml");
				final PrintWriter p = res.getWriter();
				p.print(trace);
				p.close();
			}
			else
				throw new Exception("GPSTrace is null");
		}
		catch (final Throwable e)
		{
			log.error(e.getMessage(), e);
		}
		finally
		{
			em.close();
		}

		return null;
	}

	@RequestMapping(value = "/admin/serve/imageitem/{key}", 
					method = RequestMethod.GET)
	public ModelAndView serveImageItem(final HttpServletRequest req, 
								   	   final HttpServletResponse res,
								   	   @PathVariable("key") final String key)
	{
		final EntityManager em = EMFSingleton.getEntityManager();

		try
		{
			final ImageItem i = em.find(ImageItem.class, key);

			if (i != null && i.getImage() != null)
			{
				try
				{
					File image = new File(i.getImage());
					ImageInputStream iis = 
						ImageIO.createImageInputStream(image);
					Iterator<ImageReader> readers = 
						ImageIO.getImageReaders(iis);
					String fmt = "png";
					while (readers.hasNext()) 
					{
						ImageReader read = readers.next();
						fmt = read.getFormatName();
						System.out.println("*** format name = " + fmt);
					}

					OutputStream out = res.getOutputStream();
					ImageIO.write(ImageIO.read(image), fmt, out);
					out.close();
				}
				catch (final IOException e)
				{
					log.error(e.toString());
				}
			}
		}
		catch (final Throwable e)
		{
			log.error(e.getMessage(), e);
		}
		finally
		{
			em.close();
		}

		return null;
	}

	@RequestMapping(value = "/admin/serve/videoitem/{key}", 
					method = RequestMethod.GET)
	public ModelAndView serveVideoItem(final HttpServletRequest req, 
								   	   final HttpServletResponse res,
								   	   @PathVariable("key") final String key)
	{
		return null;
	}

	@RequestMapping(value = "/admin/serve/audioitem/{key}", 
					method = RequestMethod.GET)
	public ModelAndView serveAudioItem(final HttpServletRequest req, 
								   	   final HttpServletResponse res,
								   	   @PathVariable("key") final String key)
	{	
		final EntityManager em = EMFSingleton.getEntityManager();

		try
		{
			final AudioItem a = em.find(AudioItem.class, key);

			if (a != null && a.getAudio() != null)
			{
				try
				{
					File audio = new File(a.getAudio());
					
					final ByteArrayOutputStream bos = 
						new ByteArrayOutputStream();
					final FileInputStream fis = new FileInputStream(audio);
					final BufferedInputStream bis = 
						new BufferedInputStream(fis);

					final byte data[] = new byte[2048];
					int i;
					while ((i = bis.read(data, 0, 2048)) != -1)
					{
						bos.write(data, 0, i);
					}
					fis.close();

					String[] split = getExtension(a.getAudio());
					if (split != null)
					{
						final ServletOutputStream sos = res.getOutputStream();
						res.setContentType("audio/" + split[1]);
						res.addHeader("Content-Disposition", 
									  "attachment; filename=" 
									  + audio.getName());
						
						sos.write(bos.toByteArray());
						sos.flush();
					}
					else
						throw new Exception("Error getting file suffix");
				}
				catch (final IOException e)
				{
					log.error(e.toString());
				}
			}
		}
		catch (final Throwable e)
		{
			log.error(e.getMessage(), e);
		}
		finally
		{
			em.close();
		}

		return null;
	}

	
	// Helper class for passing around general PlaceBookItem data
	private static class ItemData
	{
		private Geometry geometry;
		private User owner;
		private URL sourceURL;

		public ItemData()
		{
		}

		public Geometry getGeometry()
		{
			return geometry;
		}

		public User getOwner()
		{
			return owner;
		}

		public URL getSourceURL()
		{
			return sourceURL;
		}

		public void setGeometry(final Geometry geometry)
		{
			this.geometry = geometry;
		}

		public void setOwner(final User owner)
		{
			this.owner = owner;
		}

		public void setSourceURL(final URL sourceURL)
		{
			this.sourceURL = sourceURL;
		}
	}


	// Assumes currently open EntityManager
	private static boolean processItemData(final ItemData i, 
										   final EntityManager pm, 
										   final String field,
										   final String value)
	{
		if (field.equals("owner"))
		{
			i.setOwner(UserManager.getUser(pm, value));
		}
		else if (field.equals("sourceurl"))
		{
			try
			{
				i.setSourceURL(new URL(value));
			}
			catch (final java.net.MalformedURLException e)
			{
				log.error(e.toString());
			}
		}
		else if (field.equals("geometry"))
		{
			try
			{
				i.setGeometry(new WKTReader().read(value));
			}
			catch (final ParseException e)
			{
				log.error(e.toString());
			}
		}
		else
		{
			return false;
		}
		return true;
	}

	private String[] getExtension(final String field)
	{
		final int delim = field.indexOf(".");
		if (delim == -1)
			return null;

		String[] out = new String[2];
		out[0] = field.substring(0, delim);
		out[1] = field.substring(delim + 1, field.length());

		return out;
	}

}

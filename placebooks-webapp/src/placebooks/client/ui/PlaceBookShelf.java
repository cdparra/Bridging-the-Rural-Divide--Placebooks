package placebooks.client.ui;

import placebooks.client.model.PlaceBookEntry;
import placebooks.client.model.Shelf;

import com.google.gwt.core.client.GWT;
import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

public class PlaceBookShelf extends Composite
{
	interface PlaceBookLibraryUiBinder extends UiBinder<Widget, PlaceBookShelf>
	{
	}

	private static PlaceBookLibraryUiBinder uiBinder = GWT.create(PlaceBookLibraryUiBinder.class);

	@UiField
	Panel placebooks;

	@UiField
	Label titleLabel;

	@UiField
	PlaceBookToolbar toolbar;

	public PlaceBookShelf(final String title, final PlaceController placeController, final Shelf shelf)
	{
		initWidget(uiBinder.createAndBindUi(this));

		Window.setTitle("PlaceBooks " + title);
		titleLabel.setText(title);

		toolbar.setPlaceController(placeController);
		toolbar.setShelf(shelf);
		toolbar.setShelfListener(new PlaceBookToolbarLogin.ShelfListener()
		{
			@Override
			public void shelfChanged(final Shelf shelf)
			{
				setShelf(shelf);
			}
		});
		GWT.log("Browse " + title + ": " + shelf);
		setShelf(shelf);
	}

	private void setShelf(final Shelf shelf)
	{
		placebooks.clear();
		if (shelf != null)
		{
			for (final PlaceBookEntry entry : shelf.getEntries())
			{
				placebooks.add(new PlaceBookEntryWidget(toolbar.getPlaceController(), entry));
			}
		}
	}
}

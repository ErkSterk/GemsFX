package com.dlsc.gemsfx;

import com.dlsc.gemsfx.skins.StripViewSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.Duration;

/**
 * A view which can be used to lay out a fixed number of items horizontally. If the
 * available width is not sufficient then scrolling buttons will appear on either side
 * of the view so that the user can make hidden items visible. The nice thing about
 * this control is that it uses a {@link MaskedView} to fade out the elements on the
 * side. This ensures that the scroll buttons will be fully visible.
 *
 * @param <T> the type of the items shown by the view
 */
public class StripView<T> extends Control {

    /**
     * Constructs a new strip view.
     */
    public StripView() {
        getStyleClass().add("strip-view");

        setPrefWidth(400);
        setPrefHeight(50);
        setFadingSize(100);

        setCellFactory(strip -> new StripCell<>());

        selectedItemProperty().addListener(it -> {
            if (getSelectedItem() != null && isAutoScrolling()) {
                scrollTo(getSelectedItem());
                requestLayout();
            }
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new StripViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return StripView.class.getResource("strip-view.css").toExternalForm();
    }

    private final BooleanProperty alwaysCenter = new SimpleBooleanProperty(this, "alwaysCenter", true);

    public final boolean isAlwaysCenter() {
        return alwaysCenter.get();
    }

    /**
     * A flag used to signal whether the currently selected item should always end up in
     * the center location of the view (if possible).
     *
     * @return true if the selected item will be centered
     */
    public final BooleanProperty alwaysCenterProperty() {
        return alwaysCenter;
    }

    public final void setAlwaysCenter(boolean alwaysCenter) {
        this.alwaysCenter.set(alwaysCenter);
    }

    private final DoubleProperty fadingSize = new SimpleDoubleProperty(this, "fadingSize", 120);

    public final double getFadingSize() {
        return fadingSize.get();
    }

    /**
     * Specifies the size of the fade in / out areas on the left- and right-hand side.
     *
     * @return the size of the fading areas / the clips used for fading
     */
    public final DoubleProperty fadingSizeProperty() {
        return fadingSize;
    }

    public final void setFadingSize(double fadingSize) {
        this.fadingSize.set(fadingSize);
    }

    // Autoscrolling.

    private final BooleanProperty autoScrolling = new SimpleBooleanProperty(this, "autoScrolling", true);

    /**
     * Determines whether the view will automatically scroll to a newly selected item.
     *
     * @return true if the control uses automatic scrolling when an item becomes selected
     */
    public final BooleanProperty autoScrollingProperty() {
        return autoScrolling;
    }

    public final boolean isAutoScrolling() {
        return autoScrolling.get();
    }

    public final void setAutoScrolling(boolean autoScrolling) {
        this.autoScrolling.set(autoScrolling);
    }

    // Animation support.

    private final BooleanProperty animateScrolling = new SimpleBooleanProperty(this, "animateScrolling", true);

    /**
     * Enables or disables whether animation is being used when scrolling to the left or right.
     *
     * @return true if the scroll operation will be animated
     */
    public final BooleanProperty animateScrollingProperty() {
        return animateScrolling;
    }

    public final boolean isAnimateScrolling() {
        return animateScrolling.get();
    }

    public final void setAnimateScrolling(boolean animateScrolling) {
        this.animateScrolling.set(animateScrolling);
    }

    // Animation duration support.

    private final ObjectProperty<Duration> animationDuration = new SimpleObjectProperty<>(this, "animationDuration", Duration.millis(200));

    /**
     * Determines the duration of the scroll animation.
     *
     * @return the scroll animation duration
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    public final void setAnimationDuration(Duration animationDuration) {
        this.animationDuration.set(animationDuration);
    }

    // Selection model support.

    public final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>(this, "selectedItem");

    /**
     * Stores the currently selected item.
     *
     * @return the selected item
     */
    public final ObjectProperty<T> selectedItemProperty () {
        return selectedItem;
    }

    public final T getSelectedItem () {
        return selectedItemProperty().get();
    }

    public final void setSelectedItem (T selectedItem) {
        selectedItemProperty().set(selectedItem);
    }

    // items support

    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());

    /**
     * The model used by the strip view control.
     *
     * @return the list of items shown in the view
     */
    public final ListProperty<T> itemsProperty() {
        return items;
    }

    public final ObservableList<T> getItems() {
        return items.get();
    }

    public final void setItems(ObservableList<T> items) {
        this.items.set(items);
    }

    // cell factory support

    private final ObjectProperty<Callback<StripView<T>, StripCell<T>>> cellFactory = new SimpleObjectProperty<>(this, "cellFactory");

    public final Callback<StripView<T>, StripCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * A factory used for creating cells that will display the items added to the strip view.
     *
     * @return the cell factory
     */
    public final ObjectProperty<Callback<StripView<T>, StripCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    public final void setCellFactory(Callback<StripView<T>, StripCell<T>> cellFactory) {
        this.cellFactory.set(cellFactory);
    }

    public void scrollTo(T item) {
        getProperties().put("scroll.to", item);
    }

    /**
     * A strip cell is being used by cell factories of the {@link StripView} control.
     *
     * @param <T> the type of the model object shown by the cell
     */
    public static class StripCell<T> extends Label {

        private static final PseudoClass PSEUDO_CLASS_SELECTED = PseudoClass.getPseudoClass("selected");

        private final InvalidationListener selectionListener = it -> updateSelection();

        private final WeakInvalidationListener weakSelectionListener = new WeakInvalidationListener(selectionListener);

        /**
         * Constructs a new cell.
         */
        public StripCell() {
            getStyleClass().add("strip-cell");

            setMaxWidth(Double.MAX_VALUE);
            setMaxHeight(Double.MAX_VALUE);

            stripViewProperty().addListener((it, oldStrip, newStrip) -> {
                if (oldStrip != null) {
                    oldStrip.selectedItemProperty().removeListener(weakSelectionListener);
                }

                if (newStrip != null) {
                    newStrip.selectedItemProperty().addListener(weakSelectionListener);
                    updateSelection();
                }
            });

            itemProperty().addListener(it -> {
                setText(getItem() == null ? "" : getItem().toString());
                updateSelection();
            });
        }

        private void updateSelection() {
            T selectedItem = getStripView().getSelectedItem();
            setSelected(selectedItem == getItem());
        }

        private final ObjectProperty<StripView<T>> stripView = new SimpleObjectProperty<>(this, "stripView");

        /**
         * Stores a reference back to the strip view where the cell is
         * being used.
         *
         * @return the parent control
         */
        public final ObjectProperty<StripView<T>> stripViewProperty() {
            return stripView;
        }

        public final StripView<T> getStripView() {
            return stripView.get();
        }

        public final void setStripView(StripView<T> stripView) {
            this.stripView.set(stripView);
        }

        private BooleanProperty selected;

        public final void setSelected(boolean value) {
            selectedProperty().set(value);
        }

        public final boolean isSelected() {
            return selected != null && selected.get();
        }

        /**
         * Defines whether a cell is currently selected or not.
         *
         * @return true if the cell is selected
         */
        public final BooleanProperty selectedProperty() {
            if (selected == null) {
                selected = new BooleanPropertyBase() {
                    @Override protected void invalidated() {
                        boolean selected = get();
                        pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, selected);
                        notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
                    }

                    @Override
                    public Object getBean() {
                        return StripCell.this;
                    }

                    @Override
                    public String getName() {
                        return "selected";
                    }
                };
            }
            return selected;
        }

        private final ObjectProperty<T> item = new SimpleObjectProperty<>(this, "item");

        public final T getItem() {
            return item.get();
        }

        /**
         * The model item shown in the cell.
         *
         * @return the model item
         */
        public final ObjectProperty<T> itemProperty() {
            return item;
        }

        public final void setItem(T item) {
            this.item.set(item);
        }
    }
}

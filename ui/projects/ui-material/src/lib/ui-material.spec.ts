import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UiMaterial } from './ui-material';

describe('UiMaterial', () => {
  let component: UiMaterial;
  let fixture: ComponentFixture<UiMaterial>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UiMaterial]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UiMaterial);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
